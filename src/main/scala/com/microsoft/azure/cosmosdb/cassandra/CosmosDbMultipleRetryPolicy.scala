package com.microsoft.azure.cosmosdb.cassandra

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.oss.driver.api.core.config.DriverOption
import com.datastax.oss.driver.api.core.context.DriverContext
import com.datastax.oss.driver.api.core.retry.{RetryDecision, RetryPolicy}
import com.datastax.oss.driver.api.core.servererrors.{CoordinatorException, WriteType}
import com.datastax.oss.driver.api.core.session.Request
import com.datastax.spark.connector.cql.MultipleRetryPolicy


class CosmosDbMultipleRetryPolicy(context: DriverContext, profileName: String)
  extends MultipleRetryPolicy(context: DriverContext, profileName: String) {

  /**
    * The retry policy performs growing/fixed back-offs for overloaded exceptions based on the max retries:
    * 1. If Max retries == -1, i.e., retry infinitely, then we follow a fixed back-off scheme of 5 seconds on each retry.
    * 2. If Max retries != -1, and is any positive number n, then we follow a growing back-off scheme of (i*1) seconds where 'i' is the i'th retry.
    * If you'd like to modify the back-off intervals, please update GrowingBackOffTimeMs and FixedBackOffTimeMs accordingly.
    */
  val GrowingBackOffTimeMs: Int = 1000
  val FixedBackOffTimeMs: Int = 5000

  private val maxRetryCount = context.getConfig.getProfile(profileName).getInt(
    CosmosDbMultipleRetryPolicy.MaxRetryCount, CosmosDbMultipleRetryPolicy.MaxRetryCountDefault)

  private def retryManyTimesOrThrow(nbRetry: Int): RetryDecision = maxRetryCount match {
    case -1 => 
      Thread.sleep(FixedBackOffTimeMs)
      RetryDecision.IGNORE
    case maxRetries =>
      if (nbRetry < maxRetries) {
        Thread.sleep(GrowingBackOffTimeMs * nbRetry)
        RetryDecision.IGNORE
      } else {
        RetryDecision.RETHROW
      }
  }

  override def onReadTimeout(
    request: Request,
    cl: ConsistencyLevel,
    blockFor: Int,
    received: Int,
    dataPresent: Boolean,
    retryCount: Int): RetryDecision = retryManyTimesOrThrow(retryCount)

  override def onWriteTimeout(
    request: Request,
    cl: ConsistencyLevel,
    writeType: WriteType,
    blockFor: Int,
    received: Int,
    retryCount: Int): RetryDecision = retryManyTimesOrThrow(retryCount)

  override def onUnavailable(
    request: Request,
    cl: ConsistencyLevel,
    required: Int,
    alive: Int,
    retryCount: Int): RetryDecision = retryManyTimesOrThrow(retryCount)

  override def onRequestAborted(
    request: Request,
    error: Throwable,
    retryCount: Int): RetryDecision = retryManyTimesOrThrow(retryCount)

  override def onErrorResponse(
    request: Request,
    error: CoordinatorException,
    retryCount: Int): RetryDecision = RetryDecision.RETHROW

  override def close(): Unit = {}
}

object CosmosDbMultipleRetryPolicy {
  val MaxRetryCount: DriverOption = new DriverOption {
    override def getPath: String = "advanced.retry-policy.max-retry-count"
  }

  val MaxRetryCountDefault: Int = 60
}