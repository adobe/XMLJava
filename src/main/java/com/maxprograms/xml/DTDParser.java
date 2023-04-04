/*******************************************************************************
 * Copyright (c) 2023 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.xml;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

public class DTDParser {

    private static Logger logger = LoggerFactory.getLogger(DTDParser.class);

    private Map<String, ElementDecl> elementDeclMap;
    private Map<String, AttlistDecl> attributeListMap;
    private Map<String, EntityDecl> entitiesMap;
    private Map<String, NotationDecl> notationsMap;

    private boolean debug = false;

    public DTDParser() {
        elementDeclMap = new HashMap<>();
        attributeListMap = new HashMap<>();
        entitiesMap = new HashMap<>();
        notationsMap = new HashMap<>();
    }

    public Grammar parse(File file) throws SAXException, IOException {
        String source = readFile(file);
        String sourceAbsolutePath = file.getAbsolutePath();
        int pointer = 0;
        while (pointer < source.length()) {
            if (lookingAt("%", source, pointer)) {
                // Parameter-entity references
                int index = source.indexOf(";", pointer);
                if (index == -1) {
                    throw new SAXException(Messages.getString("DTDParser.0"));
                }
                String entityName = source.substring(pointer + "%".length(), index);
                if (!entitiesMap.containsKey(entityName)) {
                    MessageFormat mf = new MessageFormat(Messages.getString("DTDParser.1"));
                    throw new SAXException(mf.format(new String[] { entityName }));
                }
                EntityDecl entity = entitiesMap.get(entityName);
                String module = entity.getValue();
                if (module == null || StringUtils.isBlank(module)) {
                    MessageFormat mf = new MessageFormat(Messages.getString("DTDParser.2"));
                    throw new IOException(mf.format(new String[] { entityName }));
                }
                String path = XMLUtils.getAbsolutePath(file.getParentFile().getAbsolutePath(), module);
                File mod = new File(path);
                if (mod.exists()) {
                    parse(mod);
                } else {
                    if (debug) {
                        MessageFormat mf = new MessageFormat(Messages.getString("DTDParser.3"));
                        logger.warn(mf.format(new String[] { mod.getAbsolutePath() }));
                    }
                }
                pointer += "%".length() + entityName.length() + ";".length();
            }
            if (lookingAt("<!ELEMENT", source, pointer)) {
                int index = source.indexOf(">", pointer);
                if (index == -1) {
                    throw new SAXException(Messages.getString("DTDParser.4"));
                }
                String elementText = source.substring(pointer, index + ">".length());
                ElementDecl elementDecl = new ElementDecl(elementText);
                elementDeclMap.put(elementDecl.getName(), elementDecl);
                pointer += elementText.length();
                continue;
            }
            if (lookingAt("<!ATTLIST", source, pointer)) {
                int index = source.indexOf(">", pointer);
                if (index == -1) {
                    throw new SAXException(Messages.getString("DTDParser.5"));
                }
                String attListText = source.substring(pointer, index + ">".length());
                AttlistDecl attList = new AttlistDecl(attListText);
                attributeListMap.put(attList.getListName(), attList);
                pointer += attListText.length();
                continue;
            }
            if (lookingAt("<!ENTITY", source, pointer)) {
                int index = source.indexOf(">", pointer);
                if (index == -1) {
                    throw new SAXException(Messages.getString("DTDParser.6"));
                }
                String entityDeclText = source.substring(pointer, index + ">".length());
                EntityDecl entityDecl = new EntityDecl(entityDeclText, sourceAbsolutePath);
                if (!entitiesMap.containsKey(entityDecl.getName())) {
                    entitiesMap.put(entityDecl.getName(), entityDecl);
                } else {
                    if (debug) {
                        MessageFormat mf = new MessageFormat(Messages.getString("DTDParser.7"));
                        logger.warn(mf.format(new String[] { entityDecl.getName() }));
                    }
                }
                pointer += entityDeclText.length();
                continue;
            }
            if (lookingAt("<!NOTATION", source, pointer)) {
                int index = source.indexOf(">", pointer);
                if (index == -1) {
                    throw new SAXException(Messages.getString("DTDParser.8"));
                }
                String notationDeclText = source.substring(pointer, index + ">".length());
                NotationDecl notation = new NotationDecl(notationDeclText);
                if (!notationsMap.containsKey(notation.getName())) {
                    notationsMap.put(notation.getName(), notation);
                }
                pointer += notationDeclText.length();
                continue;
            }
            if (lookingAt("<?", source, pointer)) {
                int index = source.indexOf("?>", pointer);
                if (index == -1) {
                    throw new SAXException(Messages.getString("DTDParser.9"));
                }
                String piText = source.substring(pointer, index + "?>".length());
                // ignore processing instructions
                pointer += piText.length();
                continue;
            }
            if (lookingAt("<!--", source, pointer)) {
                int index = source.indexOf("-->", pointer);
                if (index == -1) {
                    throw new SAXException(Messages.getString("DTDParser.10"));
                }
                String commentText = source.substring(pointer, index);
                // ignore comments
                pointer += commentText.length() + "-->".length();
                continue;
            }
            if (lookingAt("]]>", source, pointer)) {
                pointer += +"]]>".length();
            }
            if (lookingAt("<![", source, pointer)) {
                int end = source.indexOf("]]>", pointer);
                String section = source.substring(pointer, end + "]]>".length());
                int open = count("<![", section);
                int close = count("]]>", section);
                while (open != close) {
                    end = source.indexOf("]]>", end + 1);
                    section = source.substring(pointer, end + "]]>".length());
                    open = count("<![", section);
                    close = count("]]>", section);
                }
                String type = getSectionType(section);
                if ("INCLUDE".equals(type)) {
                    int sectionStart = source.indexOf("[", pointer + "<![".length());
                    if (sectionStart == -1) {
                        throw new SAXException(Messages.getString("DTDParser.11"));
                    }
                    String skip = source.substring(pointer, sectionStart + "[".length());
                    pointer += skip.length();
                } else if ("IGNORE".equals(type)) {
                    pointer += section.length();
                    continue;
                } else {
                    throw new SAXException(Messages.getString("DTDParser.11"));
                }
            }
            if (pointer < source.length()) {
                char c = source.charAt(pointer);
                if (XMLUtils.isXmlSpace(c)) {
                    pointer++;
                    continue;
                }
                MessageFormat before = new MessageFormat(Messages.getString("DTDParser.12"));
                logger.error( before.format(new String[] { source.substring(pointer - 20, pointer) }));
                MessageFormat after = new MessageFormat(Messages.getString("DTDParser.13"));
                logger.error( after.format(new String[] { source.substring(pointer, pointer + 20) }));
                MessageFormat mf = new MessageFormat(Messages.getString("DTDParser.14"));
                throw new SAXException(mf.format(new String[] { file.getAbsolutePath() }));
            }
        }
        return new Grammar(elementDeclMap, attributeListMap, entitiesMap, notationsMap);
    }

    private int count(String target, String section) {
        int count = 0;
        int index = section.indexOf(target);
        while (index != -1) {
            count++;
            index = section.indexOf(target, index + 1);
        }
        return count;
    }

    private String getSectionType(String section) throws SAXException {
        int i = "<![".length();
        for (; i < section.length(); i++) {
            char c = section.charAt(i);
            if (!XMLUtils.isXmlSpace(c)) {
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (; i < section.length(); i++) {
            char c = section.charAt(i);
            if (XMLUtils.isXmlSpace(c) || c == '[') {
                break;
            }
            sb.append(c);
        }
        String type = sb.toString();
        while ((type.startsWith("%") || type.startsWith("&")) && type.endsWith(";")) {
            String entityName = type.substring(1, type.length() - 1);
            if (!entitiesMap.containsKey(entityName)) {
                MessageFormat mf = new MessageFormat(Messages.getString("DTDParser.1"));
                throw new SAXException(mf.format(new String[] { entityName }));
            }
            EntityDecl entity = entitiesMap.get(entityName);
            type = entity.getValue();
        }
        return type;
    }

    private boolean lookingAt(String search, String source, int start) {
        int length = search.length();
        if (length + start > source.length()) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (source.charAt(start + i) != search.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private String readFile(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (FileReader reader = new FileReader(file)) {
            try (BufferedReader buffer = new BufferedReader(reader)) {
                String line = "";
                while ((line = buffer.readLine()) != null) {
                    if (StringUtils.isNotEmpty(builder)) {
                        builder.append('\n');
                    }
                    builder.append(line);
                }
            }
        }
        return builder.toString();
    }
}