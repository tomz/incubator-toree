package com.ibm.spark.utils

import com.ibm.spark.kernel.protocol.v5.KernelMessage

trait MessageLogSupport extends LogLike {
  /**
   * Logs various pieces of a KernelMessage at different levels of logging.
   * @param km
   */
  def logMessage(km: KernelMessage): Unit = {
    logger.trace(s"Kernel message ids: ${km.ids}")
    logger.trace(s"Kernel message signature: ${km.signature}")
    logger.debug(s"Kernel message header id: ${km.header.msg_id}")
    logger.debug(s"Kernel message header type: ${km.header.msg_type}")
    km.parentHeader match {
      case null =>
        logger.warn(s"Parent header is null for message ${km.header.msg_id} of type ${km.header.msg_type}")
      case _ =>
        logger.trace(s"Kernel message parent id: ${km.parentHeader.msg_id}")
        logger.trace(s"Kernel message parent type: ${km.parentHeader.msg_type}")
    }
    logger.trace(s"Kernel message metadata: ${km.metadata}")
    logger.trace(s"Kernel message content: ${km.contentString}")
  }

  /**
   * Logs an action, along with message id and type for a KernelMessage.
   * @param action
   * @param km
   */
  def logKernelMessageAction(action: String, km: KernelMessage): Unit = {
    logger.debug(s"${action} KernelMessage ${km.header.msg_id} " +
      s"of type ${km.header.msg_type}")
  }

}
