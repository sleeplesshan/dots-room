package dev.dots.room
import org.junit.Test
import org.junit.Assert.*
class VisibilityOwnersTest {
 @Test fun rotatedActivityDoesNotDisconnectNewActivity(){val visible=VisibilityOwners();val old=Any();val new=Any();assertTrue(visible.update(old,true));assertTrue(visible.update(new,true));assertTrue(visible.update(old,false));assertTrue(visible.update(new,true));assertFalse(visible.update(new,false));assertFalse(visible.update(old,false))}
}
