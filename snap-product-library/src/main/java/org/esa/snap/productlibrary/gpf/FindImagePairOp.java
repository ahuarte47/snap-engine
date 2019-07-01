/*
 * Copyright (C) 2016 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap.productlibrary.gpf;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.dataio.ProductReader;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.MetadataAttribute;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.productlibrary.db.DBSearch;
import org.esa.snap.productlibrary.db.ProductEntry;
import org.esa.snap.engine_utilities.gpf.CommonReaders;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;

/**
 * Find image pair from the database
 */
@OperatorMetadata(alias = "Find-Image-Pair",
        category = "Input-Output",
        version = "1.0",
        authors = "Luis Veci",
        copyright = "Copyright (C) 2016 by Array Systems Computing Inc.",
        description = "DB query to find matching image pair")
public class FindImagePairOp extends Operator {

    @SourceProduct
    private Product sourceProduct;

    private transient ProductReader productReader;

    @Parameter(description = "Minimum days apart from master", defaultValue = "1", label = "Minimum days apart")
    private double minMJD = 1;

    @Parameter(description = "Maximum days apart from master", defaultValue = "99999", label = "Maximum days apart")
    private double maxMJD = 99999;

    @Override
    public void initialize() throws OperatorException {
        try {
            final ProductEntry[] entryList = DBSearch.search(sourceProduct, 1, minMJD, maxMJD);
            if (entryList.length > 0) {

                final File file = entryList[0].getFile();
                final Product targetProduct = CommonReaders.readProduct(file);
                this.productReader = targetProduct.getProductReader();
                targetProduct.setFileLocation(file);
                super.setTargetProduct(targetProduct);

                updateMetadata();
            } else {
                throw new OperatorException("No image pair found in database");
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    private void updateMetadata() {
        final Product targetProduct = super.getTargetProduct();
        final MetadataElement root = targetProduct.getMetadataRoot();
        if (root == null) {
            return;
        }
        MetadataElement abstractedMetadata = root.getElement("Abstracted_Metadata");
        if (abstractedMetadata == null) {
            return;
        }
        MetadataElement productElem = abstractedMetadata.getElement("Product_Information");
        if (productElem == null) {
            productElem = new MetadataElement("Product_Information");
            abstractedMetadata.addElement(productElem);
        }
        MetadataElement inputElem = productElem.getElement("InputProducts");
        if (inputElem == null) {
            inputElem = new MetadataElement("InputProducts");
            productElem.addElement(inputElem);
        }

        final MetadataAttribute[] inputProductAttrbList = inputElem.getAttributes();
        boolean found = false;
        for (MetadataAttribute attrib : inputProductAttrbList) {
            if (attrib.getData().getElemString().equals(targetProduct.getName()))
                found = true;
        }
        if (!found) {
            final MetadataAttribute inputAttrb = addAttribute(inputElem, "InputProduct", ProductData.TYPE_ASCII, "", "");
            inputAttrb.getData().setElems(targetProduct.getName());
        }
    }

    public static MetadataAttribute addAttribute(final MetadataElement dest, final String tag, final int dataType,
                                                 final String unit, final String desc) {
        final MetadataAttribute attribute = new MetadataAttribute(tag, dataType, 1);
        attribute.setUnit(unit);
        attribute.setDescription(desc);
        attribute.setReadOnly(true);
        dest.addAttribute(attribute);
        return attribute;
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        ProductData dataBuffer = targetTile.getRawSamples();
        Rectangle rectangle = targetTile.getRectangle();
        try {
            productReader.readBandRasterData(band, rectangle.x, rectangle.y, rectangle.width,
                    rectangle.height, dataBuffer, pm);
            targetTile.setRawSamples(dataBuffer);
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(FindImagePairOp.class);
        }
    }
}
