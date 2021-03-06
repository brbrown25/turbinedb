package com.sparcedge.turbine.services

import akka.actor.{Actor,ActorRef,Props,ActorLogging}
import scala.collection.mutable
import scala.util.Random
import spray.routing.{HttpService, RequestContext}
import spray.can.Http
import spray.util._
import spray.http._
import MediaTypes._

import com.sparcedge.turbine.Collection
import com.sparcedge.turbine.query.Match
import com.sparcedge.turbine.event.{Event,EventIngressPackage,EventPackage}
import com.sparcedge.turbine.data.QueryUtil

object StreamingNotifier {
	case class StreamEventPackage(eventIngressPkg: EventIngressPackage, eventStr: String)
	case class StreamEventPackages(eventIngressPkgs: Iterable[EventIngressPackage])
	case class StreamEvent(event: Event, eventStr: String)
	case class NewListener(ctx: RequestContext, collection: Collection, matches: Iterable[Match])
	case class Unregister(listener: ActorRef, collection: Collection)
}

import StreamingNotifier._

class StreamingNotifier extends Actor with ActorLogging {

	val collectionListenerMap = mutable.Map[Collection,mutable.ListBuffer[ActorRef]]()

	def receive = {
		case NewListener(ctx, collection, matches) =>
			val listeners = collectionListenerMap.getOrElseUpdate(collection, mutable.ListBuffer[ActorRef]())
			listeners += createListener(ctx, matches, collection)
		case StreamEventPackage(eiPkg, eventStr) =>
			val stMsg = StreamEvent(EventPackage.convertIngressEventToEvent(eiPkg.event), eventStr)
			collectionListenerMap.getOrElseUpdate(eiPkg.collection, mutable.ListBuffer[ActorRef]()).foreach(_ ! stMsg)
		case StreamEventPackages(eiPkgs) =>
			if(eiPkgs.size > 0) {
				val listeners = collectionListenerMap.getOrElseUpdate(eiPkgs.head.collection, mutable.ListBuffer[ActorRef]())
				eiPkgs.foreach { pkg =>
					val eventJson = EventIngressPackage.eventJson(pkg)
					val stMsg = StreamEvent(EventPackage.convertIngressEventToEvent(pkg.event), eventJson)
					listeners.foreach(_ ! stMsg)
				}
			}
		case Unregister(listener, coll) =>
			collectionListenerMap(coll) -= listener
		case _ =>
	}

	def createListener(ctx: RequestContext, matches: Iterable[Match], coll: Collection): ActorRef = {
		context.actorOf(Props(new EventListener(ctx, matches, coll)), s"${Random.nextDouble}-streamer")
	}

}

class EventListener(ctx: RequestContext, matches: Iterable[Match], collection: Collection) extends Actor with ActorLogging {

	val responseStart = HttpResponse(entity = """{"connected":true}""" + "\n")
	ctx.responder ! ChunkedResponseStart(responseStart)

	def receive = {
		case StreamEvent(event, eventStr) =>
			if(QueryUtil.eventMatchesAllCriteria(event,matches)) {
				ctx.responder ! MessageChunk(eventStr + "\n")
			}
		case c: Http.ConnectionClosed =>
			log.debug("Closing event listener streaming: {}", c)
			context.parent ! Unregister(self, collection)
			context.stop(self)
		case _ =>
	}

}