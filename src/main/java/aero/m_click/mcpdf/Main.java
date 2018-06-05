/**
 * Mcpdf is a drop-in replacement for PDFtk.
 *
 * It fixes PDFtk's unicode issues when filling in PDF forms,
 * and is essentially a command line interface for the iText
 * PDF library with a PDFtk compatible syntax.
 */

/*
 * Copyright (C) 2014  Volker Grabsch <grabsch@m-click.aero>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * https://www.gnu.org/licenses/agpl-3.0.html
 */

package aero.m_click.mcpdf;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.*;

public class Main
{
    public static final String DEST = "template/finalTax.pdf";

    public static void main(String[] args)
    {
        try {
            execute(parseArgs(args));
        } catch (Exception e) {
            System.err.println(e);
            System.err.println("See README for more information.");
            System.exit(1);
        }
    }

    public static Config parseArgs(String[] args)
        throws FileNotFoundException
    {
        if (args.length == 0) {
            throw new RuntimeException("Missing arguments.");
        }
        Config config = new Config();
        config.pdfInputStream = new FileInputStream(args[0]);
        config.pdfOutputStream = System.out;
        config.formInputStream = null;
        config.stampFilename = "";
        config.backgroundFilename = "";
        config.flatten = false;
        config.mergePath = "";
        for (int i = 1; i < args.length; i++) {
            if ("stamp".equals(args[i])) {
                i++;
                config.stampFilename = args[i];
            } else if ("background".equals(args[i])) {
                i++;
                config.backgroundFilename = args[i];
            } else if ("fill_form".equals(args[i])) {
                config.formInputStream = System.in;
                i++;
                if (!"-".equals(args[i])) {
                    throw new RuntimeException("Missing \"-\" after fill_form operation.");
                }
            } else if ("output".equals(args[i])) {
                i++;
                if (!"-".equals(args[i])) {
                    throw new RuntimeException("Missing \"-\" after output operation.");
                }
            } else if ("flatten".equals(args[i])) {
                config.flatten = true;
            } else if ("merge".equals(args[i])){
                config.merge = true;
                config.mergePath = System.getProperty("mergePath");
            }
            else {
                throw new RuntimeException("Unknown operation: " + args[i]);
            }
        }
        return config;
    }

    public static void execute(Config config)
        throws IOException, DocumentException
    {
        if (config.merge){
            File file = new File(DEST);
            file.getParentFile().mkdirs();
            File filled_forms_folder = new File(config.mergePath);

            File[] listOfFiles = filled_forms_folder.listFiles();
            if (listOfFiles!=null){
                Arrays.sort(listOfFiles);
                Document document = new Document();
                PdfCopy copy = new PdfCopy(document, new FileOutputStream(DEST));
                document.open();
                for (int i = 0; i < listOfFiles.length; i++) {
                    if (listOfFiles[i].isFile()) {
                        PdfReader copied_file = new PdfReader(new FileInputStream(listOfFiles[i].getPath()));
                        copy.addDocument(copied_file);
                    }
                }
                document.close();
            }else{
                throw new RuntimeException("No files in filled_forms directory.");
            }
        }else{
            PdfReader reader = new PdfReader(config.pdfInputStream);
            PdfStamper stamper = new PdfStamper(reader, config.pdfOutputStream, '\0');
            if (!config.stampFilename.isEmpty()) {
                stampBackground(reader, stamper, config.stampFilename, true);
            }
            if (!config.backgroundFilename.isEmpty()) {
                stampBackground(reader, stamper, config.backgroundFilename, false);
            }
            if (config.formInputStream != null) {
                new Main().customSetFields(new XfdfReader(config.formInputStream), stamper);
            }
            stamper.setFormFlattening(config.flatten);
            stamper.close();
        }
    }

    public static void stampBackground(PdfReader reader, PdfStamper stamper, String signature, boolean isStamp)
        throws IOException, DocumentException
    {
        int num_pages = reader.getNumberOfPages();
        PdfReader r = new PdfReader(signature);
        PdfImportedPage page = stamper.getImportedPage(r, 1);
        for (int i = 1; i <= num_pages; i++) {
            PdfContentByte canvas = isStamp ? stamper.getOverContent(i) : stamper.getUnderContent(i);
            canvas.addTemplate(page, 0, 0);
        }
        stamper.getWriter().freeReader(r);
    }

    public void customSetFields(XfdfReader xfdf, PdfStamper stamper) throws IOException, DocumentException {
        HashMap<String, String> fd = xfdf.getFields();
        for (String f: fd.keySet()) {
            String v = xfdf.getFieldValue(f);
            if (v != null)
                stamper.getAcroFields().setField(f, v, true);
            List<String> l = xfdf.getListValues(f);
            if (l != null)
                stamper.getAcroFields().setListSelection(v, l.toArray(new String[l.size()]));
        }
    }
}
