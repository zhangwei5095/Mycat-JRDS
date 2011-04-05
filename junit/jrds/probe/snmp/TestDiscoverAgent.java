package jrds.probe.snmp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import jrds.Probe;
import jrds.Tools;
import jrds.factories.ProbeMeta;
import jrds.probe.TestMeta;
import jrds.webapp.DiscoverAgent;
import junit.framework.Assert;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;

public class TestDiscoverAgent {
    static final private Logger logger = Logger.getLogger(TestMeta.class);

    @BeforeClass
    static public void configure() throws ParserConfigurationException, IOException {
        Tools.configure();
        Tools.prepareXml();

        logger.setLevel(Level.DEBUG);
        Tools.setLevel(new String[] {"jrds.DiscoverAgent.SNMP"}, logger.getLevel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getHtmlCode() throws ClassNotFoundException, InstantiationException, IllegalAccessException, TransformerException, IOException {
        Class<? extends Probe<?,?>> snmpProbeClass = (Class<? extends Probe<?,?>>) Class.forName("jrds.probe.snmp.SnmpProbe");
        ProbeMeta m = snmpProbeClass.getAnnotation(ProbeMeta.class);
        DiscoverAgent da = m.discoverAgent().newInstance();
        Assert.assertEquals(3, da.getFields().size());
        Document d = Tools.dbuilder.newDocument();
        d.appendChild(d.createElement("host"));
        da.doHtmlDiscoverFields(d);
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(OutputKeys.INDENT, "yes");
        properties.put("{http://xml.apache.org/xslt}indent-amount", "4");
        jrds.Util.serialize(d, System.out, null, properties);
    }

}
