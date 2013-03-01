package com.sparcedge.turbine

import java.io.File
import scala.util.{Try,Success,Failure}
import akka.actor.{Actor,Props,ActorSystem,ActorRef}
import akka.routing.RoundRobinRouter
import spray.routing.RequestContext
import spray.http.{HttpResponse,HttpEntity,StatusCodes}
import journal.io.api.Journal

import com.sparcedge.turbine.event.EventIngressPackage
import com.sparcedge.turbine.data.{BladeManager,WriteHandler}
import com.sparcedge.turbine.ejournal.{JournalReader,JournalWriter}
import com.sparcedge.turbine.query.{TurbineQueryPackage,TurbineQuery,QueryHandler}

object TurbineManager {
	case class QueryDispatchRequest(rawQuery: String, domain: String, tenant: String, category: String, ctx: RequestContext)
	case class AddEventRequest(rawEvent: String, domain: String, tenant: String, category: String, ctx: RequestContext)
	// TODO: Nasty Nasty Hack
	var universalEventWrittenListener: ActorRef = null
}

import TurbineManager._
import QueryHandler._
import BladeManager._
import JournalWriter._

class TurbineManager() extends Actor {

	val journal = new Journal
	initializeJournal()

	val bladeRepositoryManager = context.actorOf(Props(new BladeManagerRepository()), "BladeRepositoryManager")
	val writeHandlerRouter = context.actorOf (
		Props(new WriteHandler(bladeRepositoryManager)).withRouter(RoundRobinRouter(50)), "WriteHandlerRouter"
	)
	val queryHandlerRouter = context.actorOf (
		Props(new QueryHandler(bladeRepositoryManager)).withRouter(RoundRobinRouter(50)), "QueryHandlerRouter"
	)	
	val journalReader = context.actorOf (
		Props(new JournalReader(journal, writeHandlerRouter)), "JournalReader"
	)
	val journalWriter = context.actorOf (
		Props(new JournalWriter(journal)), "JournalWriter"
	)
	universalEventWrittenListener = journalReader

	def receive = {
		case QueryDispatchRequest(rawQuery, domain, tenant, category, ctx) =>
			TurbineQuery.tryParse(rawQuery) match {
				case Success(query) =>
					val coll = Collection(domain, tenant, category)
					val queryPackage = TurbineQueryPackage(coll, query)
					queryHandlerRouter ! HandleQuery(queryPackage, ctx)
				case Failure(err) =>
					err.printStackTrace()
					ctx.complete(HttpResponse(StatusCodes.InternalServerError))
			}
		case AddEventRequest(rawEvent, domain, tenant, category, ctx) =>
			EventIngressPackage.tryParse(rawEvent, domain, tenant, category) match {
				case Success(eventIngressPkg) =>
					journalWriter ! WriteEventToJournal(eventIngressPkg, ctx)
				case Failure(err) =>
					err.printStackTrace()
					ctx.complete(HttpResponse(StatusCodes.InternalServerError))
			}
		case _ =>
	}

	def initializeJournal() {
		val journalDir = context.system.settings.config.getString("com.sparcedge.turbinedb.journal.directory")
		ensureJournalDirectoryExists(journalDir)
		journal.setDirectory(new File(journalDir))
		journal.open()
	}

	def ensureJournalDirectoryExists(dir: String) {
		new File(dir).mkdirs()
	}
}