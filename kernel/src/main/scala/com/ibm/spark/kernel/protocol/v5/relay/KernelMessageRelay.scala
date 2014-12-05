package com.ibm.spark.kernel.protocol.v5.relay

import akka.actor.{Actor, Stash}
import akka.pattern.ask
import akka.util.Timeout
import com.ibm.spark.kernel.protocol.v5.MessageType.MessageType
import com.ibm.spark.kernel.protocol.v5.{KernelMessage, MessageType, _}
import com.ibm.spark.utils.{MessageLogSupport, LogLike}

import scala.concurrent.duration._

/**
 * This class is meant to be a relay for send KernelMessages through kernel system.
 * @param actorLoader The ActorLoader used by this class for finding actors for relaying messages
 */
case class KernelMessageRelay(
  actorLoader: ActorLoader,
  useSignatureManager: Boolean
) extends Actor with MessageLogSupport with Stash {
  // NOTE: Required to provide the execution context for futures with akka
  import context._

  // NOTE: Required for ask (?) to function... maybe can define elsewhere?
  implicit val timeout = Timeout(5.seconds)

  // Flag indicating if can receive messages (or add them to buffer)
  var isReady = false

  def this(actorLoader: ActorLoader) =
    this(actorLoader, true)

  /**
   * Relays a KernelMessage to a specific actor to handle that message
   * @param kernelMessage The message to relay
   */
  private def relay(kernelMessage: KernelMessage) = {
    val messageType: MessageType = MessageType.withName(kernelMessage.header.msg_type)
    logKernelMessageAction("Relaying", kernelMessage)
    actorLoader.load(messageType) ! kernelMessage
  }


  /**
   * This actor will receive and handle two types; ZMQMessage and KernelMessage.
   * These messages will be forwarded to the actors that are responsible for them.
   */
  override def receive = {
    // TODO: How to restore this when the actor dies?
    // Update ready status
    case ready: Boolean =>
      isReady = ready
      if (isReady) {
        logger.info("Unstashing all messages received!")
        unstashAll()
        logger.info("Relay is now fully ready to receive messages!")
      } else {
        logger.info("Relay is now disabled!")
      }

    // Add incoming messages (when not ready) to buffer to be processed
    case (zmqStrings: Seq[_], kernelMessage: KernelMessage) if !isReady =>
      logger.info("Not ready for messages! Stashing until ready!")
      stash()

    // Assuming these messages are incoming messages
    case (zmqStrings: Seq[_], kernelMessage: KernelMessage) if isReady =>
      if (useSignatureManager) {
        logger.trace(s"Verifying signature for incoming message " +
          s"${kernelMessage.header.msg_id}")
        val signatureManager = actorLoader.load(SystemActorType.SignatureManager)
        val signatureVerificationFuture = signatureManager ? ((
          kernelMessage.signature, zmqStrings
        ))

        // TODO: Handle error case for mapTo and non-present onFailure
        signatureVerificationFuture.mapTo[Boolean] onSuccess {
          // Verification successful, so continue relay
          case true => relay(kernelMessage)

          // TODO: Figure out what the failure message structure should be!
          // Verification failed, so report back a failure
          case false =>
            logger.error(s"Invalid signature received from message " +
              s"${kernelMessage.header.msg_id}!")
        }
      } else {
        logger.debug(s"Relaying incoming message " +
          s"${kernelMessage.header.msg_id} without SignatureManager")
        relay(kernelMessage)
      }

    // Assuming all kernel messages without zmq strings are outgoing
    case kernelMessage: KernelMessage =>

      if (useSignatureManager) {
        logger.trace(s"Creating signature for outgoing message " +
          s"${kernelMessage.header.msg_id}")
        val signatureManager = actorLoader.load(SystemActorType.SignatureManager)
        val signatureInsertFuture = signatureManager ? kernelMessage

        // TODO: Handle error case for mapTo and non-present onFailure
        signatureInsertFuture.mapTo[KernelMessage] onSuccess {
          case message => relay(message)
        }
      } else {
        logger.debug(s"Relaying outgoing message " +
          s"${kernelMessage.header.msg_id} without SignatureManager")
        relay(kernelMessage)
      }
  }
}
