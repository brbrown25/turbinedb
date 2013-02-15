package com.sparcedge.turbine

import akka.actor.{Actor,Props,ActorSystem,ActorRef}
import akka.routing.RoundRobinRouter
import spray.routing.RequestContext

import com.sparcedge.turbine.event.EventPackage
import com.sparcedge.turbine.data.{BladeManager,WriteHandler}
import com.sparcedge.turbine.query.{TurbineQueryPackage,QueryHandler,Blade}

object TurbineManager {

	case class QueryDispatchRequest(rawQuery: String, ctx: RequestContext)
	case class AddEventRequest(rawEvent: String)
}

import TurbineManager._
import QueryHandler._
import BladeManager._
import WriteHandler._

class TurbineManager(preloadBlades: List[Blade]) extends Actor {

	val bladeRepositoryManager = context.actorOf(Props(new BladeManagerRepository(preloadBlades)), "BladeRepositoryManager")
	val queryHandlerRouter = context.actorOf (
		Props(new QueryHandler(bladeRepositoryManager)).withRouter(RoundRobinRouter(50)), "QueryHandlerRouter"
	)
	val writeHandlerRouter = context.actorOf (
		Props(new WriteHandler(bladeRepositoryManager)).withRouter(RoundRobinRouter(50)), "WriteHandlerRouter"
	)

	def receive = {
		case QueryDispatchRequest(rawQuery, ctx) =>
			val queryPackage = TurbineQueryPackage(rawQuery)
			queryHandlerRouter ! HandleQuery(queryPackage, ctx)
		case AddEventRequest(rawEvent) =>
			val eventPkg = EventPackage(rawEvent)
			writeHandlerRouter ! WriteEventRequest(eventPkg)
		case _ =>
	}
}