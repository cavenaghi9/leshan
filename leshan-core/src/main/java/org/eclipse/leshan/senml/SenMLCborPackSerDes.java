/*******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Boya Zhang - initial API and implementation
 *******************************************************************************/

package org.eclipse.leshan.senml;

import java.io.ByteArrayOutputStream;

import org.eclipse.leshan.core.model.ResourceModel.Type;
import org.eclipse.leshan.util.Hex;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

public class SenMLCborPackSerDes {

    public byte[] serializeToCbor(SenMLPack pack) throws SenMLCborException {
        CBORFactory factory = new CBORFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            CBORGenerator generator = factory.createGenerator(out);
            generator.writeStartArray(pack.getRecords().size());

            for (SenMLRecord record : pack.getRecords()) {
                boolean hasBaseName = false;
                boolean hasBaseTime = false;
                boolean hasName = false;
                boolean hasTime = false;
                int objectSize = 1;

                if (record.getBaseName() != null && !record.getBaseName().isEmpty()) {
                    hasBaseName = true;
                    objectSize++;
                }

                if (record.getBaseTime() != null) {
                    hasBaseTime = true;
                    objectSize++;
                }

                if (record.getName() != null && !record.getName().isEmpty()) {
                    hasName = true;
                    objectSize++;
                }

                if (record.getTime() != null) {
                    hasTime = true;
                    objectSize++;
                }

                generator.writeStartObject(objectSize);

                if (hasBaseName) {
                    generator.writeFieldId(-2);
                    generator.writeString(record.getBaseName());
                }

                if (hasBaseTime) {
                    generator.writeFieldId(-3);
                    generator.writeNumber(record.getBaseTime());
                }

                if (hasName) {
                    generator.writeFieldId(0);
                    generator.writeString(record.getName());
                }

                if (hasTime) {
                    generator.writeFieldId(6);
                    generator.writeNumber(record.getTime());
                }

                Type type = record.getType();
                if (type != null) {
                    switch (record.getType()) {
                    case FLOAT:
                        generator.writeFieldId(2);
                        generator.writeNumber(record.getFloatValue().intValue());
                        break;
                    case BOOLEAN:
                        generator.writeFieldId(4);
                        generator.writeBoolean(record.getBooleanValue());
                        break;
                    case OBJLNK:
                        generator.writeStringField("vlo", record.getObjectLinkValue());
                        break;
                    case OPAQUE:
                        generator.writeFieldId(8);
                        generator.writeBinary(record.getOpaqueValue());
                    case STRING:
                        generator.writeFieldId(3);
                        generator.writeString(record.getStringValue());
                        break;
                    default:
                        break;
                    }
                }
                generator.writeEndObject();
            }

            generator.writeEndArray();
            generator.close();
        } catch (Exception ex) {
            throw new SenMLCborException("Impossible to encode pack to CBOR: \n" + pack, ex);
        }

        return out.toByteArray();
    }

    public SenMLPack deserializeFromCbor(byte[] content) throws SenMLCborException {
        SenMLPack pack = new SenMLPack();

        try {
            CBORFactory factory = new CBORFactory();
            CBORParser parser = factory.createParser(content);

            JsonToken token = null;
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.START_ARRAY) {
                    continue;
                }

                if (token == JsonToken.START_OBJECT) {
                    SenMLRecord record = parseSenMLRecord(content, parser);
                    pack.addRecord(record);
                }

                if (token == JsonToken.END_ARRAY) {
                    break;
                }
            }
        } catch (Exception e) {
            throw new SenMLCborException(
                    "Impossible to decode SenML pack from CBOR data: \n" + Hex.encodeHexString(content), e);
        }

        return pack;
    }

    private static SenMLRecord parseSenMLRecord(byte[] content, CBORParser parser) throws SenMLCborException {
        SenMLRecord record = new SenMLRecord();
        try {
            JsonToken token = null;
            String fileName = null;
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT) {
                    return record;
                } else if (token == JsonToken.FIELD_NAME) {
                    fileName = parser.getCurrentName();
                    if (fileName != null && fileName.equals("-2")) {
                        token = parser.nextToken();
                        record.setBaseName(parser.getText());
                        continue;
                    } else if (fileName != null && fileName.equals("-3")) {
                        token = parser.nextToken();
                        record.setBaseTime(parser.getLongValue());
                    } else if (fileName != null && fileName.equals("0")) {
                        token = parser.nextToken();
                        record.setName(parser.getText());
                    } else if (fileName != null && fileName.equals("6")) {
                        token = parser.nextToken();
                        record.setTime(parser.getLongValue());
                    }
                } else {
                    if (fileName != null && fileName.equals("2")) {
                        record.setFloatValue(parser.getFloatValue());
                    } else if (fileName != null && fileName.equals("4")) {
                        record.setBooleanValue(parser.getValueAsBoolean());
                    } else if (fileName != null && fileName.equals("3")) {
                        record.setStringValue(parser.getValueAsString());
                    } else if (fileName != null && fileName.equals("8")) {
                        record.setStringValue(parser.getValueAsString());
                    } else if (fileName != null && fileName.equals("vlo")) {
                        record.setObjectLinkValue(parser.getValueAsString());
                    }
                }
            }
        } catch (Exception e) {
            throw new SenMLCborException(
                    "Impossible to decode SenML record from CBOR data: \n" + Hex.encodeHexString(content), e);
        }
    }
}
