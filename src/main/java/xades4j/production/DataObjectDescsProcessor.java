/*
 * XAdES4j - A Java library for generation and verification of XAdES signatures.
 * Copyright (C) 2010 Luis Goncalves.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */
package xades4j.production;

import xades4j.properties.DataObjectTransform;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import org.apache.xml.security.signature.ObjectContainer;
import org.apache.xml.security.signature.Reference;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignatureException;
import org.apache.xml.security.transforms.TransformationException;
import org.apache.xml.security.transforms.Transforms;
import org.w3c.dom.Element;
import xades4j.UnsupportedAlgorithmException;
import xades4j.properties.DataObjectDesc;

/**
 * Helper class that processes a ser of data object descriptions.
 * 
 * @author Luís
 */
class DataObjectDescsProcessor
{
    private DataObjectDescsProcessor()
    {
    }

    /**
     * Returns the reference mappings resulting from the data object descriptions.
     * The corresponding {@code Reference}s and {@code Object}s are added to the
     * signature.

     * @throws UnsupportedAlgorithmException
     */
    static Map<DataObjectDesc, Reference> process(
            Collection<DataObjectDesc> dataObjsDescs,
            XMLSignature xmlSignature,
            String digestMethodUri) throws UnsupportedAlgorithmException
    {
        Map<DataObjectDesc, Reference> referenceMappings = new IdentityHashMap<DataObjectDesc, Reference>(dataObjsDescs.size());

        String refUri, refType;
        Transforms transforms;
        /**/
        try
        {
            for (DataObjectDesc dataObjDesc : dataObjsDescs)
            {
                refType = null;
                transforms = processTransforms(dataObjDesc, xmlSignature);

                if (dataObjDesc instanceof DataObjectReference)
                    // If the data object info is a DataObjectReference, the Reference uri
                    // is the one specified on the object.
                    refUri = ((DataObjectReference)dataObjDesc).getUri();
                else if (dataObjDesc instanceof EnvelopedXmlObject)
                {
                    // If the data object info is a EnvelopedXmlObject we need to create a
                    // XMLObject to embed it. The Reference uri will refer the new
                    // XMLObject's id.
                    EnvelopedXmlObject envXmlObj = ((EnvelopedXmlObject)dataObjDesc);
                    refUri = String.format("%s-object%d", xmlSignature.getId(), xmlSignature.getObjectLength());
                    refType = Reference.OBJECT_URI;

                    ObjectContainer xmlObj = new ObjectContainer(xmlSignature.getDocument());
                    xmlObj.setId(refUri);
                    xmlObj.appendChild(envXmlObj.getContent());
                    xmlObj.setMimeType(envXmlObj.getMimeType());
                    xmlObj.setEncoding(envXmlObj.getEncoding());
                    xmlSignature.appendObject(xmlObj);

                    refUri = '#' + refUri;
                } else
                    throw new ClassCastException("Unsupported SignedDataObjectDesc. Must be either DataObjectReference or EnvelopedXmlObject");

                // Add the Reference. References need an ID because data object
                // properties may refer them.
                xmlSignature.addDocument(
                        refUri,
                        transforms,
                        digestMethodUri,
                        String.format("%s-ref%d", xmlSignature.getId(), referenceMappings.size()), // id
                        refType);

                // SignedDataObjects doesn't allow repeated instances, so there's no
                // need to check for duplicate entries on the map.
                Reference ref = xmlSignature.getSignedInfo().item(referenceMappings.size());
                referenceMappings.put(dataObjDesc, ref);
            }

        } catch (XMLSignatureException ex)
        {
            // -> xmlSignature.appendObject(xmlObj): not thrown when signing.
            // -> xmlSignature.addDocument(...): appears to be thrown when the digest
            //      algorithm is not supported.
            throw new UnsupportedAlgorithmException(
                    "Digest algorithm not supported in the XML Signature provider: " + ex.getMessage(),
                    digestMethodUri);
        } catch (org.apache.xml.security.exceptions.XMLSecurityException ex)
        {
            // -> xmlSignature.getSignedInfo().item(...): shouldn't be thrown
            //      when signing.
            throw new IllegalStateException(ex);
        }

        return Collections.unmodifiableMap(referenceMappings);
    }

    private static Transforms processTransforms(
            DataObjectDesc dataObjDesc,
            XMLSignature xmlSignature) throws UnsupportedAlgorithmException
    {
        Collection<DataObjectTransform> dObjTransfs = dataObjDesc.getTransforms();
        if (dObjTransfs.isEmpty())
            return null;

        Transforms transforms = new Transforms(xmlSignature.getDocument());

        for (DataObjectTransform dObjTransf : dObjTransfs)
        {
            try
            {
                Element transfParams = dObjTransf.getTransformParams();
                if (null == transfParams)
                    transforms.addTransform(dObjTransf.getTransformUri());
                else
                    transforms.addTransform(dObjTransf.getTransformUri(), transfParams);
            } catch (TransformationException ex)
            {
                throw new UnsupportedAlgorithmException(
                        "Unsupported transform on XML Signature provider",
                        dObjTransf.getTransformUri());
            }
        }
        return transforms;
    }
}