
import kotlin.math.*

data class Pose(val x: Double, val y: Double, val rotation: Double) {
    fun apply(dx: Double, dy: Double, drot: Double): Pose {
        val rad = Math.toRadians(rotation)
        val newX = x + dx * cos(rad) - dy * sin(rad)
        val newY = y + dx * sin(rad) + dy * cos(rad)
        val newRot = (rotation + drot) % 360.0
        return Pose(newX, newY, if (newRot < 0) newRot + 360.0 else newRot)
    }

    fun relativeTo(other: Pose): Pose {
        // Find transform T such that other.apply(T) = this
        val drot = (rotation - other.rotation + 360.0) % 360.0
        val rad = Math.toRadians(-other.rotation)
        val dxW = x - other.x
        val dyW = y - other.y
        val dx = dxW * cos(rad) - dyW * sin(rad)
        val dy = dxW * sin(rad) + dyW * cos(rad)
        return Pose(dx, dy, drot)
    }
}

fun main() {
    val fork = Pose(0.0, 0.0, 0.0)
    val straightEntry = Pose(0.0, 0.0, 180.0) // Connector at fork facing away
    val straightExit = Pose(32.0, 0.0, 0.0)
    val branchExitRight = Pose(32.693, 12.955, 22.5)
    val branchExitLeft = Pose(32.693, -12.955, -22.5)

    println("--- SWITCH RIGHT ---")
    val entryR = straightEntry
    val exitR = straightExit
    val branchR = branchExitRight

    // REV STRAIGHT
    // Entry is straightExit, facing fork (rotation 180)
    val revSEntry = Pose(32.0, 0.0, 180.0)
    println("REV_STRAIGHT allConnectors relative to entry:")
    println("  C1 (Exit): ${straightExit.relativeTo(revSEntry)}")
    println("  C3 (Branch Entry): ${branchExitRight.relativeTo(revSEntry)}")
    println("  C2 (Entry): ${Pose(32.0, 0.0, 180.0).relativeTo(revSEntry)}") // Entry itself
    println("  baseTransform: ${fork.relativeTo(revSEntry)}")

    // REV BRANCH
    // Entry is branchExitRight, facing fork (rotation 202.5)
    val revBEntry = Pose(32.693, 12.955, 202.5)
    println("REV_BRANCH allConnectors relative to entry:")
    println("  C1 (Exit): ${straightExit.relativeTo(revBEntry)}")
    println("  C2 (Straight Entry): ${fork.apply(0.0, 0.0, 180.0).relativeTo(revBEntry)}")
    println("  C3 (Entry): ${branchExitRight.relativeTo(revBEntry)}")
    println("  baseTransform: ${fork.relativeTo(revBEntry)}")

    println("\n--- SWITCH LEFT ---")
    // REV STRAIGHT
    val revSEntryL = Pose(32.0, 0.0, 180.0)
    println("REV_STRAIGHT allConnectors relative to entry:")
    println("  C1 (Exit): ${straightExit.relativeTo(revSEntryL)}")
    println("  C3 (Branch Entry): ${branchExitLeft.relativeTo(revSEntryL)}")
    println("  baseTransform: ${fork.relativeTo(revSEntryL)}")

    // REV BRANCH
    val revBEntryL = Pose(32.693, -12.955, 157.5)
    println("REV_BRANCH allConnectors relative to entry:")
    println("  C1 (Exit): ${straightExit.relativeTo(revBEntryL)}")
    println("  C2 (Straight Entry): ${fork.apply(0.0, 0.0, 180.0).relativeTo(revBEntryL)}")
    println("  baseTransform: ${fork.relativeTo(revBEntryL)}")
}
