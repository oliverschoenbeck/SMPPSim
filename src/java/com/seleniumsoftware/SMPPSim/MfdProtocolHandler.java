package com.seleniumsoftware.SMPPSim;

import com.seleniumsoftware.SMPPSim.exceptions.InboundQueueFullException;
import com.seleniumsoftware.SMPPSim.exceptions.OutboundQueueFullException;
import com.seleniumsoftware.SMPPSim.pdu.*;
import com.seleniumsoftware.SMPPSim.util.LoggingUtilities;

import java.util.ArrayList;
import java.util.logging.Logger;

public class MfdProtocolHandler extends StandardProtocolHandler {
    private static Logger logger = Logger.getLogger("com.seleniumsoftware.smppsim");

    public MfdProtocolHandler() {
        if (SMPPSim.isSimulate_variable_submit_sm_response_times()) {
            delay_delta = (int) (Math.random() * 100) + 1;
        }
    }

    public String getName() {
        return ("MFDProtocolHandler");
    }

    private static final long MAX_DELAY = 3000;
    private long delay;
    private long delay_delta;

    /**
     * Custom variation of submitSM handler for MFD.
     *
     * This one treats each non numeric character in a destination MSISDN as an error
     * and responds with ESME_RINVDSTADR in such a case. The SMSC at OEBB side seam to
     * behave like this.
     */
    void getSubmitSMResponse(byte[] message, int len) throws Exception {
        LoggingUtilities.hexDump(": Standard SUBMIT_SM:", message, len);
        byte[] resp_message;
        SubmitSM smppmsg = new SubmitSM();
        smppmsg.demarshall(message);
        if (smsc.isDecodePdus())
            LoggingUtilities.logDecodedPdu(smppmsg);
        smsc.writeDecodedSme(smppmsg.toString());
        logger.info(" ");

        // now make the response object
        SubmitSMResp smppresp = new SubmitSMResp(smppmsg);

        // Validate session
        if ((!session.isBound()) || (!session.isTransmitter())) {
            logger.warning("Invalid bind state. Must be bound as transmitter for this PDU");
            wasInvalidBindState = true;
            resp_message = smppresp.errorResponse(smppresp.getCmd_id(), PduConstants.ESME_RINVBNDSTS, smppresp.getSeq_no());
            logPdu(":SUBMIT_SM_RESP (ESME_RINVBNDSTS):", resp_message, smppresp);
            connection.writeResponse(resp_message);
            smsc.writeDecodedSmppsim(smppresp.toString());
            smsc.incSubmitSmERR();
            return;
        }

        // Validation
        // empty destination
        if (smppmsg.getDestination_addr().equals("")) {
            resp_message = smppresp.errorResponse(smppresp.getCmd_id(),
                    PduConstants.ESME_RINVDSTADR, smppresp.getSeq_no());
            logPdu(":SUBMIT_SM_RESP (ESME_RINVDSTADR):", resp_message, smppresp);
            connection.writeResponse(resp_message);
            smsc.writeDecodedSmppsim(smppresp.toString());
            smsc.incSubmitSmERR();
            return;
        }

        // Invalid MSISDN if contains non numeric characters
        // - ÖBB SMSC seam to behave like this.
        try { long n = Long.parseLong(smppmsg.getDestination_addr()); }
        catch (NumberFormatException nfe) {
            resp_message = smppresp.errorResponse(smppresp.getCmd_id(), PduConstants.ESME_RINVDSTADR, smppresp.getSeq_no());
            logPdu(":SUBMIT_SM_RESP (ESME_RINVDSTADR):", resp_message, smppresp);
            connection.writeResponse(resp_message);
            smsc.writeDecodedSmppsim(smppresp.toString());
            smsc.incSubmitSmERR();
            return;
        }

        // Try to add to the OutboundQueue for lifecycle tracking
        MessageState m = null;
        try {
            m = new MessageState(smppmsg, smppresp.getMessage_id());
            smsc.getOq().addMessageState(m);
        } catch (OutboundQueueFullException e) {
            logger.warning("OutboundQueue full.");
            resp_message = smppresp.errorResponse(smppresp.getCmd_id(), PduConstants.ESME_RMSGQFUL, smppresp.getSeq_no());
            logPdu(":SUBMIT_SM_RESP (ESME_RMSGQFUL):", resp_message, smppresp);
            smsc.incSubmitSmERR();
            connection.writeResponse(resp_message);
            smsc.writeDecodedSmppsim(smppresp.toString());
            return;
        }
        // ....and turn it back into a byte array
        resp_message = smppresp.marshall();

        if (SMPPSim.isSimulate_variable_submit_sm_response_times()) {
            try {
                logger.info("Delaying response by "+delay+"ms");
                Thread.sleep(delay);
                long per_message_delta = (int) (Math.random() * 500) - 250; // between -250 and 250 or thereabouts
                delay = delay + delay_delta + per_message_delta;
                if (delay > MAX_DELAY) {
                    delay = MAX_DELAY;
                    delay_delta = delay_delta * -1;
                } else {
                    if (delay < 0) {
                        delay = 0;
                        delay_delta = delay_delta * -1;
                    }
                }
            } catch (InterruptedException e) {
            }
        }

        logPdu(":SUBMIT_SM_RESP:", resp_message, smppresp);
        logger.info(" ");
        connection.writeResponse(resp_message);
        logger.info("SubmitSM processing - response written to connection");
        smsc.writeDecodedSmppsim(smppresp.toString());
        // set messagestate responsesent = true
        smsc.getOq().setResponseSent(m);
        smsc.incSubmitSmOK();

        // If loopback is switched on, have an SMPPReceiver object deliver this
        // message back to the client
        if (SMPPSim.isLoopback()) {
            try {
                smsc.doLoopback(smppmsg);
            } catch (InboundQueueFullException e) {
                logger.warning("Failed to create loopback DELIVER_SM because the Inbound Queue is full");
            }
        } else {
            if (SMPPSim.isEsme_to_esme()) {
                try {
                    smsc.doEsmeToEsmeDelivery(smppmsg);
                } catch (InboundQueueFullException e) {
                    logger.warning("Failed to create ESME to ESME DELIVER_SM because the Inbound Queue is full");
                }
            }
        }
    }
}
