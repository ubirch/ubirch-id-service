package com.ubirch.models

/**
 * Represents a type for wrapping the kind of data and the data itself.
 * It is meant for easier management of the data value
 * @param category Represents the kind of data it is
 * @param data Represents the data that is actually packed in.
 */
case class Raw(category: RAWCategory, data: String)

/**
 * Represents the Category of the Data stored in a Raw element
 */
sealed trait RAWCategory

/**
 * Case for JSON
 */
case object JSON extends RAWCategory

/**
 * Case for Message Pack
 */
case object MSG_PACK extends RAWCategory

/**
 * Case for a X059 cert
 */
case object CERT extends RAWCategory
