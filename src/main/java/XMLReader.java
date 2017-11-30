import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

public class XMLReader {

    public static void main(String argv[]) {
        new XMLReader().parseXML("src\\main\\resources\\commands.xml");

    }

    public void parseXML(String pathName){
        try {
//            File file = new File("src\\main\\resources\\MyXMLFile.xml");
            File file = new File(pathName);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(file);
            doc.getDocumentElement().normalize();
            NodeList nodes = doc.getElementsByTagName("command");

            for (int s = 0; s < nodes.getLength(); s++) {
                Node fstNode = nodes.item(s);
                if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element rootElement = (Element) fstNode;

                    System.out.println("name : " + getNodeValue(rootElement, "name"));
                    System.out.println("description: " + getNodeValue(rootElement, "description"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getNodeValue(Element rootElement, String nodeName){
        NodeList list = rootElement.getElementsByTagName(nodeName);
        Element element = (Element) list.item(0);
        NodeList childNodes = element.getChildNodes();

        return childNodes.item(0).getNodeValue();
    }
}
