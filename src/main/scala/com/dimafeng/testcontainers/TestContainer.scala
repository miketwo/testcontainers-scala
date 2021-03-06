package com.dimafeng.testcontainers

import java.io.File

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.Bind
import org.junit.runner.Description
import org.scalatest._
import org.testcontainers.containers.traits.LinkableContainer
import org.testcontainers.containers.{
FailureDetectingExternalResource, TestContainerAccessor, DockerComposeContainer => OTCDockerComposeContainer, GenericContainer => OTCGenericContainer
}
import org.testcontainers.utility.Base58

import scala.collection.JavaConverters._
import scala.concurrent.Future

trait ForEachTestContainer extends SuiteMixin {
  self: Suite =>

  val container: Container

  implicit private val suiteDescription = Description.createSuiteDescription(self.getClass)

  abstract protected override def runTest(testName: String, args: Args): Status = {
    container.starting()
    try {
      afterStart()
      val status = super.runTest(testName, args)
      status match {
        case FailedStatus => container.failed(new RuntimeException(status.toString))
        case _ => container.succeeded()
      }
      status
    }
    catch {
      case e: Throwable =>
        container.failed(e)
        throw e
    }
    finally {
      try {
        beforeStop()
      }
      finally {
        container.finished()
      }
    }
  }

  def afterStart(): Unit = {}

  def beforeStop(): Unit = {}
}

trait ForAllTestContainer extends SuiteMixin {
  self: Suite =>

  val container: Container

  implicit private val suiteDescription = Description.createSuiteDescription(self.getClass)

  abstract override def run(testName: Option[String], args: Args): Status = {
    if (expectedTestCount(args.filter) == 0) {
      new CompositeStatus(Set.empty)
    } else {
      container.starting()
      try {
        afterStart()
        super.run(testName, args)
      } finally {
        try {
          beforeStop()
        }
        finally {
          container.finished()
        }
      }
    }
  }

  def afterStart(): Unit = {}

  def beforeStop(): Unit = {}
}

trait Container {
  def finished()(implicit description: Description): Unit

  def failed(e: Throwable)(implicit description: Description): Unit

  def starting()(implicit description: Description): Unit

  def succeeded()(implicit description: Description): Unit
}

class DockerComposeContainer(composeFiles: Seq[File], exposedService: Map[String, Int] = Map(), identifier: String)
  extends TestContainerProxy[OTCDockerComposeContainer[_]] {

  type OTCContainer = OTCDockerComposeContainer[T] forSome {type T <: OTCDockerComposeContainer[T]}
  override val container: OTCContainer = new OTCDockerComposeContainer(identifier, composeFiles.asJava)
  exposedService.foreach(Function.tupled(container.withExposedService))

  def getServiceHost(serviceName: String, servicePort: Int): String = container.getServiceHost(serviceName, servicePort)

  def getServicePort(serviceName: String, servicePort: Int): Int = container.getServicePort(serviceName, servicePort)
}

object DockerComposeContainer {
  val ID_LENGTH = 6

  def apply(composeFiles: Seq[File],
            exposedService: Map[String, Int],
            identifier: String): DockerComposeContainer =
    new DockerComposeContainer(composeFiles, exposedService, identifier)

  def apply(composeFile: File, exposedService: Map[String, Int] = Map()): DockerComposeContainer =
    apply(Seq(composeFile), exposedService, Base58.randomString(ID_LENGTH).toLowerCase())
}

trait TestContainerProxy[T <: FailureDetectingExternalResource] extends Container {
  implicit val container: T

  override def finished()(implicit description: Description): Unit = TestContainerAccessor.finished(description)

  override def succeeded()(implicit description: Description): Unit = TestContainerAccessor.succeeded(description)

  override def starting()(implicit description: Description): Unit = TestContainerAccessor.starting(description)

  override def failed(e: Throwable)(implicit description: Description): Unit = TestContainerAccessor.failed(e, description)
}

abstract class SingleContainer[T <: OTCGenericContainer[_]] extends TestContainerProxy[T] {

  def binds: Seq[Bind] = container.getBinds.asScala

  def command: Seq[String] = container.getCommandParts

  def containerId: String = container.getContainerId

  def containerInfo: InspectContainerResponse = container.getContainerInfo

  def containerIpAddress: String = container.getContainerIpAddress

  def containerName: String = container.getContainerName

  def env: Seq[String] = container.getEnv.asScala

  def exposedPorts: Seq[Int] = container.getExposedPorts.asScala.map(_.intValue())

  def extraHosts: Seq[String] = container.getExtraHosts.asScala

  import scala.concurrent.ExecutionContext.Implicits.global

  def image: Future[String] = Future {
    container.getImage.get()
  }

  def linkedContainers: Map[String, LinkableContainer] = container.getLinkedContainers.asScala.toMap

  def mappedPort(port: Int): Int = container.getMappedPort(port)

  def portBindings: Seq[String] = container.getPortBindings.asScala
}