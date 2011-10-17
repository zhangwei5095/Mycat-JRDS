package jrds;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import jrds.probe.IndexedProbe;
import jrds.probe.UrlProbe;
import jrds.starter.ChainedProperties;
import jrds.starter.StarterNode;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;

/**
 *
 * @author Fabrice Bacchella 
 * @version $Revision$,  $Date$
 */
public class Util {
    static final private Logger logger = Logger.getLogger(Util.class);

    static private MessageDigest md5digest;
    static {
        try {
            md5digest = java.security.MessageDigest.getInstance("MD5");
        }
        catch (java.security.NoSuchAlgorithmException ex) {
            logger.fatal("You should not see this message, MD5 not available");
        }
    }

    private static final String BASE64_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+_=";
    private static final char[] BASE64_CHARSET = BASE64_CHARS.toCharArray();

    private static final Pattern varregexp = Pattern.compile("(.*?)\\$\\{([\\w\\.-]+)\\}(.*)");
    private static final Pattern oldvarregexp = Pattern.compile("(.*?[^\\$])??\\{(\\d+)\\}(.*)");

    /**
     * The SI prefix as an enumeration, with factor provided.<p/>
     * More informations can be found at <a target="_blank" href="http://en.wikipedia.org/wiki/SI_prefix">Wikipedia's page</a> 
     */
    public enum SiPrefix {
        Y(24),
        Z(21),
        E(18),
        P(15),
        T(12),
        G(9),
        M(6),
        k(3),
        h(2),
        da(1),
        FIXED(0),
        d(-1),
        c(-2),
        m(-3),
        µ(-6),
        n(-9),
        p(-12),
        f(-15),
        a(-18),
        z(-21),
        y(-24);

        private int exponent;
        private SiPrefix(int exponent) {
            this.exponent = exponent;
        }

        /**
         * Evaluate a value in the context of this prefix
         * @param value the value to evalute
         * @param isSi is the prefix metric or binary (power of 2)
         * @return the raw value
         */
        public double evaluate(double value, boolean isSi) {
            return Math.pow(isSi ? 10 : 1024, isSi ? exponent: exponent/3.0 ) * value;
        }

        /**
         * @return the exponent for this prefix
         */
        public int getExponent() {
            return exponent;
        }
    };

    static final private ErrorListener el = new ErrorListener() {
        public void error(TransformerException e) throws TransformerException {
            logger.error("Invalid xsl: " + e.getMessageAndLocation());
        }
        public void fatalError(TransformerException e) throws TransformerException {
            logger.fatal("Invalid xsl: " + e.getMessageAndLocation());
        }
        public void warning(TransformerException e) throws TransformerException {
            logger.warn("Invalid xsl: " + e.getMessageAndLocation());
        }
    };
    static final TransformerFactory tFactory = TransformerFactory.newInstance();
    static {
        tFactory.setErrorListener(el);
    }

    /**
     * Return the md5 digest value of a string, encoded in base64
     * @param The string to use
     * @return the printable md5 digest value for s
     */
    public static String stringSignature(String s)
    {
        byte[] digestval;
        synchronized(md5digest) {
            md5digest.reset();
            digestval = md5digest.digest(s.getBytes());
        }
        return toBase64(digestval);
    }

    /**

     * <p>Converts a designated byte array to a Base-64 representation, with the	 
     * exceptions that (a) leading 0-byte(s) are ignored, and (b) the character	 
     * '.' (dot) shall be used instead of "+' (plus).</p>	 

     * @param buffer an arbitrary sequence of bytes to represent in Base-64.	 
     * @return unpadded (without the '=' character(s)) Base-64 representation of	 
     * the input.

     */
    private static String toBase64(byte[] buffer) {
        int len = buffer.length;
        int pos = 0;
        StringBuffer sb = new StringBuffer((int) (len * 1.4) + 3 );

        while(pos < len) {
            byte b0 = 0, b1 = 0, b2 = 0;
            b0 = buffer[pos++];
            if(pos  < len )
                b1 = buffer[pos++];
            if(pos  < len)
                b2 = buffer[pos++];
            int c0 = (b0 & 0xFC) >>> 2;
            sb.append(BASE64_CHARSET[c0]);
            int c1 = ((b0 & 0x03) << 4) | ((b1 & 0xF0) >>> 4);
            sb.append(BASE64_CHARSET[c1]);
            int c2 = ((b1 & 0x0F) << 2) | ((b2 & 0xC0) >>> 6);
            sb.append(BASE64_CHARSET[c2]);
            int c3 = b2 & 0x3F;
            sb.append(BASE64_CHARSET[c3]);
        }
        int mod = len %3;
        if(mod == 2)
            sb.deleteCharAt(sb.length() -1 );
        else if(mod == 1) {
            sb.deleteCharAt(sb.length() -1 );
            sb.deleteCharAt(sb.length() -1 );
        }
        return sb.toString();	
    }

    public static String cleanPath(String s){
        String retval = s.replace('\\', '_');
        retval = retval.replace(':', '_');
        retval = retval.replace('/', '_');
        return retval;
    }

    /**
     * Used to normalize the end date to the last update time
     * but only if it's close to it 
     * @param p the probe to check against
     * @param endDate the desired end date
     * @return the normalized end date
     */
    public static Date endDate(Probe<?,?> p, Date endDate) {
        //Date normalized = endDate;
        //We normalize the last update time, it can't be used directly
        long step = p.getStep();
        Date lastUpdate = p.getLastUpdate();

        //We dont want to graph past the last normalized update time
        //but only if we are within a step interval
        if( Math.abs(endDate.getTime() - lastUpdate.getTime()) <= (step * 1000L))
            return normalize(lastUpdate, step);

        //Else rrd4j will manage the normalization itself
        return endDate;
    }

    /**
     * Normalize to a probe step, as org.rrd4j.core.Util.normalize
     * But use a Date argument and return a Date
     * @param date	A Date to normalize
     * @param step	Step in seconds
     * @return "Rounded" Date
     */
    public static Date normalize(Date date, long step) {
        long timestamp = org.rrd4j.core.Util.getTimestamp(date);
        return org.rrd4j.core.Util.getDate(org.rrd4j.core.Util.normalize(timestamp, step));
    }

    public static String evaluateVariables(String in, Map<String, Object> variables, StarterNode node) {
        ChainedProperties props = null;
        if(node != null)
            props = node.find(ChainedProperties.class);
        return evaluateVariables(in, variables, props);
    }

    /**
     * Evaluate a string containing variables in the form ${varname}
     * 
     * variable is map of variable name pointing to object that will be evaluted
     * with their toString method to get variable names
     * 
     * Any variable starting with system. is a system property
     * @param in
     * @param variables
     * @param props
     * @return
     */
    public static String evaluateVariables(String in, Map<String, Object> variables, Map<String,String> props) {
        Matcher m = varregexp.matcher(in);
        if(m.find()) {
            StringBuilder out = new StringBuilder();
            if(m.groupCount() == 3) {
                String before = m.group(1);
                String var = m.group(2);
                String after = m.group(3);
                out.append(before);
                String toAppend = null;
                if(var.startsWith("system.")) {
                    toAppend = System.getProperty(var.replace("system.", ""));
                }
                else if(variables.containsKey(var)) {
                    toAppend = variables.get(var).toString();
                }
                else if(props != null) {
                    String propsValue = props.get(var);
                    if(propsValue != null)
                        toAppend = propsValue;
                }
                if(toAppend == null) {
                    toAppend = "${" + var + "}";
                }
                out.append(toAppend);
                if(after.length() > 0)
                    out.append(evaluateVariables(after, variables, props));
                return out.toString();
            }
        }
        return in;
    }
    
    /**
     * A method to parse a template mixing old elements {x} with new variable ${variable}
     * Should be not be used any more
     * @param template The template to parse
     * @param keys a array to match indexes elements
     * @param arguments some object to extract value. from
     * @return
     */
    public static String parseOldTemplate(String template, Object[] keys, Object... arguments) {
        //Don't lose time with an empty template
        if(template == null || "".equals(template.trim())) {
            return template;
        }

        Matcher m = oldvarregexp.matcher(template);
        String last = template;
        StringBuffer buffer = new StringBuffer();
        while(m.find()) {
            if(m.group(1) !=  null)
                buffer.append(m.group(1));
            buffer .append(keys[Integer.parseInt(m.group(2))]);
            last = m.group(3);
            m = oldvarregexp.matcher(last);
        }
        buffer.append(last);
        return jrds.Util.parseTemplate(buffer.toString(), arguments);
    }

    @SuppressWarnings("unchecked")
    public static String parseTemplate(String template, Object... arguments) {
        //Don't lose time with an empty template
        if(template == null || "".equals(template.trim())) {
            return template;
        }

        Map<String, Object> env = new HashMap<String, Object>();
        StarterNode node = null;
        for(Object o: arguments) {
            if(logger.isTraceEnabled())
                logger.trace("Argument for template \"" + template + "\": " + o.getClass());
            if( o instanceof IndexedProbe) {
                String index = ((IndexedProbe) o).getIndexName();
                env.put("index", index);
                env.put("index.signature", stringSignature(index));
                env.put("index.cleanpath", cleanPath(index));
            }
            if(o instanceof UrlProbe) {
                env.put("url", ((UrlProbe) o).getUrlAsString());
                env.put("port", ((UrlProbe) o).getPort());
                env.put("url.signature", jrds.Util.stringSignature(((UrlProbe) o).getUrlAsString()));
            }
            if(o instanceof ConnectedProbe) {
                env.put("connection.name", ((ConnectedProbe) o).getConnectionName());
            }
            if( o instanceof Probe) {
                Probe<?,?> p = ((Probe<?,?>) o);
                RdsHost host = p.getHost();
                if(host != null)
                    env.put("host", host.getName());
                String probename=p.getName();
                //It might be called just for evaluate probename
                //So no problem if it's null
                if(probename != null)
                    env.put("probename", probename);
                String label = p.getLabel();
                if(label != null) {
                    env.put("label", label);
                }
            } 
            if( o instanceof RdsHost) {
                env.put("host", ((RdsHost) o).getName());
            }
            if(o instanceof GraphDesc) {
                GraphDesc gd = (GraphDesc) o;
                env.put("graphdesc.title", gd.getGraphTitle());
                env.put("graphdesc.name", gd.getGraphName());
            }
            if(o instanceof ProbeDesc) {
                ProbeDesc pd = (ProbeDesc) o;
                env.put("probedesc.name", pd.getName());
            }
            if(o instanceof StarterNode) {
                node = (StarterNode) o;
            }
            if(o instanceof Map) {
                Map<? extends String, ?> tempMap = (Map<? extends String, ?>)o;
                env.putAll(tempMap);
            }
            if(o instanceof List) {
                int rank=1;
                for(Object listElem: (List<? extends Object>) o) {
                    env.put(String.valueOf(rank++), listElem.toString());
                }
            }
        }
        if(logger.isDebugEnabled())
            logger.debug("Properties to use for parsing template \"" + template + "\": " + env);

        return jrds.Util.evaluateVariables(template, env, node);
    }

    /**
     * <p>A compact and exception free number parser.<p>
     * <p>If the string can be parsed as the specified type, it return the default value<p>
     * @param toParse The string to parse
     * @param defaultVal A default value to use it the string can't be parsed
     * @return An Number object using the same type than the default value.
     */
    @SuppressWarnings("unchecked")
    public static <NumberClass extends Number> NumberClass parseStringNumber(String toParse, NumberClass defaultVal) {
        if(toParse == null || "".equals(toParse))
            return defaultVal;

        try {
            Class<NumberClass> clazz = (Class<NumberClass>) defaultVal.getClass();
            Constructor<NumberClass> c = clazz.getConstructor(String.class);
            NumberClass n = c.newInstance(toParse);
            return n;
        } catch (SecurityException e) {
        } catch (NoSuchMethodException e) {
        } catch (IllegalArgumentException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
        return defaultVal;
    }

    @Deprecated
    public static <NumberClass extends Number> NumberClass parseStringNumber(String toParse, Class<NumberClass> nc, NumberClass defaultVal) {
        if(toParse == null || "".equals(toParse))
            return defaultVal;
        if(! (Number.class.isAssignableFrom(nc))) {
            return defaultVal;
        }

        try {
            Constructor<NumberClass> c = nc.getConstructor(String.class);
            NumberClass n = c.newInstance(toParse);
            return n;
        } catch (SecurityException e) {
        } catch (NoSuchMethodException e) {
        } catch (IllegalArgumentException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
        return defaultVal;
    }

    public static void serialize(Document d, OutputStream out, URL transformerLocation, Map<String, String> properties) throws TransformerException, IOException {
        Source source = new DOMSource(d);

        Transformer transformer = null;
        if(transformerLocation != null) {
            Source stylesource = new StreamSource(transformerLocation.toString());
            transformer = tFactory.newTransformer(stylesource);
        }
        else
            transformer = tFactory.newTransformer();

        String documentEncoding = d.getXmlEncoding();
        if(documentEncoding == null)
            documentEncoding = "UTF-8";
        transformer.setOutputProperty(OutputKeys.ENCODING, documentEncoding);
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");

        DocumentType dt = d.getDoctype();
        //If no transformation, we try to keep the Document type
        if(dt != null && transformerLocation == null) {
            String publicId = dt.getPublicId();
            if(publicId != null)
                transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, publicId);
            String systemId = dt.getSystemId();
            if(systemId != null)
                transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, systemId);
        }

        if(properties != null) {
            for(Map.Entry<String, String> e: properties.entrySet()) {
                transformer.setOutputProperty(e.getKey(), e.getValue());
            }
        }

        Writer w = new OutputStreamWriter(out, documentEncoding);
        StreamResult result = new StreamResult(w);
        transformer.transform(source, result);
        out.flush();
    }

    public static <T> Iterable<T> iterate(final Enumeration<T> en) {
        final Iterator<T> iterator = new Iterator<T>() {
            public boolean hasNext() {  
                return en.hasMoreElements();  
            }
            public T next() {
                return en.nextElement();  
            }
            public void remove() {
                throw new UnsupportedOperationException("Cannot remove in XML serialization iterator");  
            }
        };
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return iterator;
            }
        };
    }

    /**
     * Return an alpha numeric sorter where host2 is before host10
     * Copied from http://sanjaal.com/java/tag/sample-alphanumeric-sorting/
     * @return
     */
    public static final Comparator<String> nodeComparator = jrds.Util.AlphanumericSorting();

    private static Comparator<String> AlphanumericSorting() {
        return new Comparator<String>() {

            public int compare(String firstString, String secondString) {

                if (secondString == null || firstString == null) {
                    return 0;
                }

                firstString = firstString.toLowerCase();
                secondString = secondString.toLowerCase();

                int lengthFirstStr = firstString.length();
                int lengthSecondStr = secondString.length();

                int index1 = 0;
                int index2 = 0;

                while (index1 < lengthFirstStr && index2 < lengthSecondStr) {
                    char ch1 = firstString.charAt(index1);
                    char ch2 = secondString.charAt(index2);

                    char[] space1 = new char[lengthFirstStr];
                    char[] space2 = new char[lengthSecondStr];

                    int loc1 = 0;
                    int loc2 = 0;

                    do {
                        space1[loc1++] = ch1;
                        index1++;

                        if (index1 < lengthFirstStr) {
                            ch1 = firstString.charAt(index1);
                        } else {
                            break;
                        }
                    } while (Character.isDigit(ch1) == Character.isDigit(space1[0]));

                    do {
                        space2[loc2++] = ch2;
                        index2++;

                        if (index2 < lengthSecondStr) {
                            ch2 = secondString.charAt(index2);
                        } else {
                            break;
                        }
                    } while (Character.isDigit(ch2) == Character.isDigit(space2[0]));

                    String str1 = new String(space1);
                    String str2 = new String(space2);

                    int result;

                    if (Character.isDigit(space1[0]) && Character.isDigit(space2[0])) {
                        Integer firstNumberToCompare = Integer.parseInt(str1.trim());
                        Integer secondNumberToCompare = Integer.parseInt(str2.trim());
                        result = firstNumberToCompare.compareTo(secondNumberToCompare);
                    } else {
                        result = str1.compareTo(str2);
                    }

                    if (result != 0) {
                        return result;
                    }
                }
                return lengthFirstStr - lengthSecondStr;
            }
        };
    }

    static public void log(Object source, Logger namedLogger, Level l, Throwable e, String format, Object... args) {
        if(namedLogger.isEnabledFor(l)) {
            StringBuilder line = new StringBuilder();
            if(source != null)
                line.append("[" + source.toString() + "] ");
            line.append(String.format(format, args));
            namedLogger.log(l, line.toString());
            //NPE should never happen, so it's always logged
            if(e != null && (namedLogger.isDebugEnabled() || e instanceof NullPointerException) ) {
                Writer w = new CharArrayWriter(e.getStackTrace().length + 20);
                e.printStackTrace(new PrintWriter(w));
                namedLogger.log(l, "Error stack: ");
                namedLogger.log(l, w);

            }
        }
    }

    static	public boolean rolesAllowed(Set<String> allowedRoles, Set<String> userRoles) {
        return ! Collections.disjoint(allowedRoles, userRoles);
    }

    /**
     * A wrapper method to delay evaluation of log4j arguments
     * @param format
     * @param args
     * @return
     */
    static public Object delayedFormatString(final String format, final Object ...args) {
        return new Object() {
            @Override
            public String toString() {
                return String.format(format, args);
            }
        };
    }

}

