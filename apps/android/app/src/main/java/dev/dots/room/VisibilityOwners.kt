package dev.dots.room
/** Rotation may start the new Activity before stopping the old Activity. */
class VisibilityOwners {
 private val visible=mutableSetOf<Any>()
 fun update(owner:Any,show:Boolean):Boolean{if(show)visible.add(owner)else visible.remove(owner);return visible.isNotEmpty()}
}
