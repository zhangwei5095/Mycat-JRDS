package jrds;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

/**
 * This class is used to setup the log environment.
 * <p>
 * The normal starting point for logger configuration is initLog4J(). But
 * putAppender() can be used instead if log4j is already configured. It that's
 * the case, the following steps must be done:
 * <ul>
 * <li>Define the jrds appender using putAppender.
 * <li>Set additivity to false for the rootLoggers if this appender is used at
 * an higher level.
 * <li>Do not define a log file in the property file or PropertiesManager
 * object.
 * </ul>
 * 
 * @author Fabrice Bacchella
 */
public class JrdsLoggerConfiguration {
    static public final String APPENDERNAME = "jrdsAppender";
    static public final String DEFAULTLOGFILE = ConsoleAppender.SYSTEM_ERR;
    static public final String DEFAULTLAYOUT = "[%d] %5p %c : %m%n";
    static public Appender jrdsAppender = null;
    // The managed loggers list
    static public final Set<String> rootLoggers = new HashSet<String>(Arrays.asList(new String[] { "jrds", "org.mortbay.log", "org.apache", "org.eclipse.jetty" }));
    // Used to check if jrds "own" the log configuration
    // null = we don't know yet
    static private Boolean logOwner = null;

    private JrdsLoggerConfiguration() {

    }

    /**
     * Force an external log4j configuration, must be called before initLog4j
     */
    static public void setExternal() {
        logOwner = false;
    }

    /**
     * The method used to prepare a minimal set of logging configuration. This
     * should be used once. It does nothing if it detect that a appender already
     * exist for the logger <code>jrds</code>. The default logger is the system
     * error output and the default level is error.
     * 
     * @throws IOException
     */
    static public void initLog4J() throws IOException {
        // Do nothing if jrds is not allowed to setup logs
        if(!isLogOwner())
            return;
        if(jrdsAppender == null) {
            jrdsAppender = new ConsoleAppender(new org.apache.log4j.SimpleLayout(), DEFAULTLOGFILE);
            jrdsAppender.setName(APPENDERNAME);
        }
        // Configure all the manager logger
        // Default level is debug, not a very good idea
        for(String loggerName: rootLoggers) {
            configureLogger(loggerName, Level.ERROR);
        }
    }

    /**
     * This method prepare the log4j environment using the configuration in
     * jrds.properties. it uses the following properties
     * <ul>
     * <li><code>logfile</code>, used to define the log file, if not defined, no
     * appender is created</li>
     * <li><code>loglevel</code>, used to define the default loglevel</li>
     * <li><code>log.&lt;level&gt;</code>, followed by a comma separated list of
     * logger, to set the level of those logger to <code>level</code></li>
     * </ul>
     * 
     * @param pm a configured PropertiesManager object
     * @throws IOException
     */
    static public void configure(PropertiesManager pm) throws IOException {
        // Do nothing if jrds is not allowed to setup logs
        if(!isLogOwner())
            return;

        if(pm.logfile != null && !"".equals(pm.logfile)) {
            jrdsAppender = new DailyRollingFileAppender(new PatternLayout(DEFAULTLAYOUT), pm.logfile, "'.'yyyy-ww");
            jrdsAppender.setName(APPENDERNAME);
        }
        for(String logger: rootLoggers) {
            configureLogger(logger, pm.loglevel);
        }
        // getLogger change the level of new loggers only
        Logger.getLogger("jrds").setLevel(pm.loglevel);

        for(Map.Entry<Level, List<String>> e: pm.loglevels.entrySet()) {
            Level l = e.getKey();
            for(String logName: e.getValue()) {
                Logger.getLogger(logName).setLevel(l);
            }
        }
    }

    /**
     * This method is used to join other logger branch with the jrds' one and
     * use same setting if it's not already defined
     * 
     * @param logname the logger name
     * @param level the desired default level for this logger
     */
    static public void configureLogger(String logname, Level level) {
        // Do nothing if jrds is not allowed to setup logs
        if(!isLogOwner())
            return;
        Logger externallogger = LogManager.getLoggerRepository().exists(logname);
        // Change level only for new logger
        if(externallogger == null) {
            externallogger = Logger.getLogger(logname);
            externallogger.setLevel(level);
        }

        // Replace the appender, not optionally add it
        if(jrdsAppender != null) {
            Logger logger = Logger.getLogger(logname);
            Appender oldApp = logger.getAppender(jrdsAppender.getName());
            if(oldApp != null)
                logger.removeAppender(oldApp);
            logger.addAppender(jrdsAppender);
            logger.setAdditivity(false);
        }

        // Keep the new logger name
        rootLoggers.add(logname);
    }

    static private synchronized boolean isLogOwner() {
        // logOwner == null mean we don't know yet
        // if will be set to false if a logger called jrds already exist
        if(logOwner == null) {
            logOwner = LogManager.getLoggerRepository().exists("jrds") == null;
        }
        return logOwner;
    }

}
