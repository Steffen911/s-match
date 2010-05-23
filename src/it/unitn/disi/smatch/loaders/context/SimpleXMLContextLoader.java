package it.unitn.disi.smatch.loaders.context;

import it.unitn.disi.smatch.components.Configurable;
import it.unitn.disi.smatch.data.ling.AtomicConceptOfLabel;
import it.unitn.disi.smatch.data.ling.IAtomicConceptOfLabel;
import it.unitn.disi.smatch.data.trees.Context;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import org.apache.log4j.Logger;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.*;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Loader for XML format.
 *
 * @author Aliaksandr Autayeu avtaev@gmail.com
 */
public class SimpleXMLContextLoader extends Configurable implements IContextLoader, ContentHandler {

    private static final Logger log = Logger.getLogger(SimpleXMLContextLoader.class);

    private static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.SAXParser";
    private XMLReader parser;

    // variables used in parsing
    // context being loaded
    private IContext ctx;
    // to collect all content in case parser processes element content in several passes
    private StringBuilder content;
    // atomic concept begin read
    private IAtomicConceptOfLabel acol;
    // path to the root node
    private Deque<INode> pathToRoot;

    private int nodesParsed = 0;

    public SimpleXMLContextLoader() throws ContextLoaderException {
        try {
            parser = XMLReaderFactory.createXMLReader(DEFAULT_PARSER_NAME);
            parser.setContentHandler(this);
            parser.setProperty("http://apache.org/xml/properties/input-buffer-size", 8196);
            pathToRoot = new ArrayDeque<INode>();
        } catch (SAXException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new ContextLoaderException(errMessage, e);
        }
    }

    public IContext loadContext(String fileName) throws ContextLoaderException {
        try {
            BufferedReader inputFile = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF-8"));
            InputSource is = new InputSource(inputFile);
            parser.parse(is);
            log.info("Parsed nodes: " + nodesParsed);
        } catch (SAXException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new ContextLoaderException(errMessage, e);
        } catch (FileNotFoundException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new ContextLoaderException(errMessage, e);
        } catch (UnsupportedEncodingException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new ContextLoaderException(errMessage, e);
        } catch (IOException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new ContextLoaderException(errMessage, e);
        }
        return ctx;
    }


    //org.xml.sax.ContentHandler methods re-implementation start

    public void startDocument() {
        ctx = new Context();
        nodesParsed = 0;
    }

    public void startElement(String namespace, String localName, String qName, Attributes atts) {
        if ("node".equals(localName)) {
            INode node;
            if (null == ctx.getRoot()) {
                node = ctx.createRoot();
            } else {
                if (0 < pathToRoot.size()) {
                    node = pathToRoot.getLast().createChild();
                } else {
                    // looks like there are multiple roots
                    INode oldRoot = ctx.getRoot();
                    INode newRoot = ctx.createRoot("Top");
                    newRoot.addChild(oldRoot);
                    node = newRoot.createChild();
                }
            }
            node.getNodeData().setId(atts.getValue("id"));
            pathToRoot.addLast(node);
        } else if ("token".equals(localName)) {
            acol = new AtomicConceptOfLabel();
            acol.setId(Integer.parseInt(atts.getValue("id")));
        } else if ("sense".equals(localName)) {
            acol.createSense(atts.getValue("pos").charAt(0), Long.parseLong(atts.getValue("id")));
        } else {
            content = new StringBuilder();
        }
    }

    public void endElement(String uri, String localName, String qName) {
        if ("name".equals(localName)) {
            pathToRoot.getLast().getNodeData().setName(content.toString());
        } else if ("label-formula".equals(localName)) {
            pathToRoot.getLast().getNodeData().setcLabFormula(content.toString());
        } else if ("node-formula".equals(localName)) {
            pathToRoot.getLast().getNodeData().setcNodeFormula(content.toString());
        } else if ("text".equals(localName)) {
            acol.setToken(content.toString());
        } else if ("lemma".equals(localName)) {
            acol.setLemma(content.toString());
        } else if ("token".equals(localName)) {
            pathToRoot.getLast().getNodeData().addACoL(acol);
        } else if ("node".equals(localName)) {
            pathToRoot.removeLast();

            nodesParsed++;
            if (0 == (nodesParsed % 1000)) {
                log.info("nodes parsed: " + nodesParsed);
            }
        }
    }

    public void characters(char[] ch, int start, int length) {
        content.append(ch, start, length);
    }

    public void setDocumentLocator(Locator locator) {
        //nop
    }

    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        //nop
    }

    public void processingInstruction(String target, String data) throws SAXException {
        //nop
    }

    public void skippedEntity(String name) throws SAXException {
        //nop
    }

    public void endDocument() {
        //nop
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        //nop
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        //nop
    }

    //org.xml.sax.ContentHandler methods re-implementation end

}