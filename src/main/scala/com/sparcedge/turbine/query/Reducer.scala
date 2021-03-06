package com.sparcedge.turbine.query

import play.api.libs.json.Json
import play.api.libs.json.{JsObject,JsString}

import com.sparcedge.turbine.event.Event

object ReducerPackage {
	def apply(jsObj: JsObject): ReducerPackage = {
		jsObj.fields.head match {
			case (propertyName: String, reducerObj: JsObject) =>
				createReducerPackage(propertyName, reducerObj)
			case _ =>
				throw new Exception("Invalid Reducer")
		}
	}

	def createReducerPackage(propertyName: String, reducerObj: JsObject): ReducerPackage = {
		reducerObj.fields.head match {
			case (reduceType: String, JsString(segment)) =>
				val reducer = reduceType match {
					case "max" => new MaxReducer(segment)
					case "min" => new MinReducer(segment)
					case "sum" => new SumReducer(segment)
					case "avg" => new AvgReducer(segment)
					case "harmonicmean" => new HarmonicMeanReducer(segment)
					case "count" => new CountReducer(segment)
					case "stdev" => new StDevReducer(segment)
					case "range" => new RangeReducer(segment)
					case "variance" => new VarianceReducer(segment)
					case _ => throw new Exception(s"Invalid Reducer Type: ${reduceType}")
				}
				ReducerPackage(propertyName, reducer)
			case _ =>
				throw new Exception("Invalid Reducer")
		}
		
	}
}

case class ReducerPackage(outputProperty: String, reducer: Reducer)

trait Reducer {
	def reduceType: String
	def segment: String

	def createReducedResult(): ReducedResult
}

case class MaxReducer(segment: String) extends Reducer {
	val reduceType = "max"
	def createReducedResult(): ReducedResult = new MaxReducedResult(segment)	
}

case class MinReducer(segment: String) extends Reducer {
	val reduceType = "min"
	def createReducedResult(): ReducedResult = new MinReducedResult(segment)
}

case class AvgReducer(segment: String) extends Reducer {
	val reduceType = "avg"
	def createReducedResult(): ReducedResult = new AvgReducedResult(segment)
}

case class SumReducer(segment: String) extends Reducer {
	val reduceType = "sum"
	def createReducedResult(): ReducedResult = new SumReducedResult(segment)
}

case class CountReducer(segment: String) extends Reducer {
	val reduceType = "count"
	def createReducedResult(): ReducedResult = new CountReducedResult(segment)	
}

case class RangeReducer(segment: String) extends Reducer {
	val reduceType = "range"
	def createReducedResult(): ReducedResult = new RangeReducedResult(segment)
}

case class VarianceReducer(segment: String) extends Reducer {
	val reduceType = "variance"
	def createReducedResult(): ReducedResult = new VarianceReducedResult(segment)	
}

case class StDevReducer(segment: String) extends Reducer {
	val reduceType = "stdev"
	def createReducedResult(): ReducedResult = new StDevReducedResult(segment)	
}

case class HarmonicMeanReducer(segment: String) extends Reducer {
	val reduceType = "harmonicmean"
	def createReducedResult(): ReducedResult = new HarmonicMeanReducedResult(segment)
}