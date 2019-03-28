package com.seleniumsoftware.SMPPSim;

import com.seleniumsoftware.SMPPSim.pdu.PduConstants;
import com.seleniumsoftware.SMPPSim.pdu.SmppTime;
import com.seleniumsoftware.SMPPSim.pdu.SubmitSM;

import java.time.Instant;
import java.util.Date;
import java.util.logging.Logger;

public class MfdLifeCycleManager extends LifeCycleManager {

    private static Logger logger = Logger.getLogger("com.seleniumsoftware.smppsim");

    private double transitionThreshold;

    private double deliveredThreshold;

    private double undeliverableThreshold;

    private double acceptedThreshold;

    private double rejectedThreshold;

    private int discardThreshold;

    private int maxTimeEnroute;

    private double transition;

    private double stateChoice;

    public MfdLifeCycleManager() {
        double a = (double) SMPPSim.getPercentageThatTransition() + 1.0;
        transitionThreshold = (a / 100);
        logger.finest("transitionThreshold=" + transitionThreshold);
        logger.finest("SMPPSim.getPercentageThatTransition()=" + SMPPSim.getPercentageThatTransition());
        maxTimeEnroute = SMPPSim.getMaxTimeEnroute();
        logger.finest("maxTimeEnroute=" + maxTimeEnroute);
        discardThreshold = SMPPSim.getDiscardFromQueueAfter();
        logger.finest("discardThreshold=" + discardThreshold);
        deliveredThreshold = ((double) SMPPSim.getPercentageDelivered() / 100);
        logger.finest("deliveredThreshold=" + deliveredThreshold);
        // .90
        undeliverableThreshold = deliveredThreshold + ((double) SMPPSim.getPercentageUndeliverable() / 100);
        logger.finest("undeliverableThreshold=" + undeliverableThreshold);
        // .90 + .06 = .96
        acceptedThreshold = undeliverableThreshold + ((double) SMPPSim.getPercentageAccepted() / 100);
        logger.finest("acceptedThreshold=" + acceptedThreshold);
        // .96 + .02 = .98
        rejectedThreshold = acceptedThreshold + ((double) SMPPSim.getPercentageRejected() / 100);
        logger.finest("rejectedThreshold=" + rejectedThreshold);
        // .98 + .02 = 1.00
    }

    public MessageState setState(MessageState m) {
        // Should a transition take place at all?
        if (isTerminalState(m.getState()))
            return m;
        byte currentState = m.getState();
        transition = Math.random();

        // Handle message validity.
        Date now = new Date();
        Date validUntil = m.getValidity_period().getDatetime();

        if(now.after(validUntil)){
            m.setState(PduConstants.EXPIRED);
            logger.finest("State set to EXPIRED due to validity date.");
        } else {
            if ((transition < transitionThreshold) || ((System.currentTimeMillis() - m.getSubmit_time()) > maxTimeEnroute)) {
                // so which transition should it be?
                stateChoice = Math.random();
                if (stateChoice < deliveredThreshold) {
                    m.setState(PduConstants.DELIVERED);
                    logger.finest("State set to DELIVERED");
                } else if (stateChoice < undeliverableThreshold) {
                    m.setState(PduConstants.UNDELIVERABLE);
                    logger.finest("State set to UNDELIVERABLE");
                } else if (stateChoice < acceptedThreshold) {
                    m.setState(PduConstants.ACCEPTED);
                    logger.finest("State set to ACCEPTED");
                } else {
                    m.setState(PduConstants.REJECTED);
                    logger.finest("State set to REJECTED");
                }
            }
        }

        if (isTerminalState(m.getState())) {
            m.setFinal_time(System.currentTimeMillis());

            // If delivery receipt requested prepare it....
            SubmitSM p = m.getPdu();
            logger.info("Message:"+p.getSeq_no()+" state="+getStateName(m.getState()));
            if (p.getRegistered_delivery_flag() == 1 && currentState != m.getState()) {
                prepDeliveryReceipt(m, p);
            } else {
                if (p.getRegistered_delivery_flag() == 2 && currentState != m.getState()) {
                    if (isFailure(m.getState())) {
                        prepDeliveryReceipt(m, p);
                    }
                }
            }
        }
        return m;
    }
}
