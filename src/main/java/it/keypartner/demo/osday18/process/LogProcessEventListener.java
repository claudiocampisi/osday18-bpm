package it.keypartner.demo.osday18.process;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jbpm.workflow.core.node.HumanTaskNode;
import org.kie.api.event.process.*;
import org.kie.api.runtime.manager.RuntimeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import javax.naming.InitialContext;
import java.lang.IllegalStateException;


public class LogProcessEventListener extends DefaultProcessEventListener {
    public static final int BEFORE_START_EVENT_TYPE = 0;
    public static final int AFTER_START_EVENT_TYPE = 1;
    public static final int BEFORE_COMPLETE_EVENT_TYPE = 2;
    public static final int AFTER_COMPLETE_EVENT_TYPE = 3;
    public static final int BEFORE_NODE_ENTER_EVENT_TYPE = 4;
    public static final int AFTER_NODE_ENTER_EVENT_TYPE = 5;
    public static final int BEFORE_NODE_LEFT_EVENT_TYPE = 6;
    public static final int AFTER_NODE_LEFT_EVENT_TYPE = 7;
    public static final int BEFORE_VAR_CHANGE_EVENT_TYPE = 8;
    public static final int AFTER_VAR_CHANGE_EVENT_TYPE = 9;
    public static final int BEFORE_WAIT_MESSAGE_EVENT_TYPE = 10;
    public static final int BEFORE_HUMAN_TASK_EVENT_TYPE = 11;



    private static final Logger log = LoggerFactory.getLogger(LogProcessEventListener.class);
    private static final String[] SKIP_CHANGE_ON_THIS_VARIABLES = { "processId", "initiator" };
    private String connectionFactoryName;
    private String destinationName;
    private ConnectionFactory connectionFactory;
    private Queue queue;
    private boolean transacted = true;



    public LogProcessEventListener(RuntimeManager runtimeManager, boolean perProcessInstanceStrategy) {
        this.connectionFactoryName = "java:/JmsXA";
        this.destinationName = "java:/jms/queue/bpm.notifier.v1.0";
        init();
    }

    public LogProcessEventListener(RuntimeManager runtimeManager, boolean perProcessInstanceStrategy, String connectionFactoryName, String destinationName  ) {
        this.connectionFactoryName = connectionFactoryName;
        this.destinationName = destinationName;
        init();
    }

    public LogProcessEventListener(boolean perProcessInstanceStrategy) {
        this.connectionFactoryName = "java:/JmsXA";
        this.destinationName = "java:/jms/queue/bpm.notifier.v1.0";
        init();
    }

    private void init() {
        try {
            InitialContext ctx = new InitialContext();
            if (this.connectionFactory == null) {
                this.connectionFactory = (ConnectionFactory) ctx.lookup(connectionFactoryName);
            }
            if (this.queue == null) {
                this.queue = (Queue) ctx.lookup(destinationName);
            }
            log.info("Workbeat LogProcessEventListener successfully activated on destination {}", queue);
        } catch (Exception e) {
            log.error("Unable to initialize Workbeat LogProcessEventListener due to {}", e.getMessage(), e);

        }
    }



    @Override
    public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {

        if(event.getNodeInstance().getNode() instanceof HumanTaskNode) {
            HumanTaskNode humanTaskNode = (HumanTaskNode) event.getNodeInstance().getNode();
            log.info("before triggered human task node {}", humanTaskNode.getName());
            NotificationInfo notificationInfo = new NotificationInfo();
            String deploymentId = (String)event.getKieRuntime().getEnvironment().get("deploymentId");
            notificationInfo.taskName = humanTaskNode.getName();
            notificationInfo.deploymentId = deploymentId;
            sendMessage(notificationInfo,BEFORE_HUMAN_TASK_EVENT_TYPE);
        }
    }


    protected void sendMessage(Object messageContent, Integer eventType) {
        if (connectionFactory == null && queue == null) {
            throw new IllegalStateException("ConnectionFactory and Queue cannot be null");
        }


        JMSContext jmsContext = null;
        try {
            jmsContext = connectionFactory.createContext();
            JMSProducer jmsProducer = jmsContext.createProducer();
            ObjectMapper mapper = new ObjectMapper();
            String eventJson = mapper.writeValueAsString(messageContent);
            jmsProducer.send(queue, eventJson);
        } catch (Exception e) {
            throw new RuntimeException("Error when sending JMS message with working memory event", e);
        } finally {
            if (jmsContext != null) {
                jmsContext.close();
            }


        }
    }

}
