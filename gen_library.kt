
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
    val straightEntry = Pose(0.0, 0.0, 180.0)
    val straightExit = Pose(32.0, 0.0, 0.0)
    val branchExitRight = Pose(32.693, 12.955, 22.5)
    val branchExitLeft = Pose(32.693, -12.955, -22.5)
    val branchEntryRight = Pose(32.693, 12.955, 202.5)
    val branchEntryLeft = Pose(32.693, -12.955, 157.5)

    println("--- SWITCH RIGHT ---")
    // REV_STRAIGHT (Entry at straightExit facing fork)
    val rsEntry = Pose(32.0, 0.0, 180.0)
    println("REV_STRAIGHT (Entry at SX facing fork):")
    println("  baseTransform: ${fork.relativeTo(rsEntry)}")
    println("  C1 (Fork Exit): ${fork.apply(0.0, 0.0, 180.0).relativeTo(rsEntry)}") // Faces 180
    println("  C3 (Branch Entry): ${branchEntryRight.relativeTo(rsEntry)}")
    println("  C2 (Entry): ${straightExit.apply(0.0,0.0,180.0).relativeTo(rsEntry)}")

    // REV_BRANCH (Entry at branchExit facing fork)
    val rbEntry = Pose(32.693, 12.955, 202.5)
    println("\nREV_BRANCH (Entry at BX facing fork):")
    println("  baseTransform: ${fork.relativeTo(rbEntry)}")
    println("  C1 (Fork Exit): ${fork.apply(0.0,0.0,180.0).relativeTo(rbEntry)}")
    println("  C2 (Straight Entry): ${Pose(0.0, 0.0, 180.0).relativeTo(rbEntry)}")
    println("  C3 (Entry): ${branchExitRight.apply(0.0,0.0,180.0).relativeTo(rbEntry)}")

    println("\n--- SWITCH LEFT ---")
    // REV_STRAIGHT
    val rsEntryL = Pose(32.0, 0.0, 180.0)
    println("REV_STRAIGHT:")
    println("  baseTransform: ${fork.relativeTo(rsEntryL)}")
    println("  C1 (Fork Exit): ${fork.apply(0.0,0.0,180.0).relativeTo(rsEntryL)}")
    println("  C3 (Branch Entry): ${branchEntryLeft.relativeTo(rsEntryL)}")

    // REV_BRANCH
    val rbEntryL = Pose(32.693, -12.955, 157.5)
    println("\nREV_BRANCH:")
    println("  baseTransform: ${fork.relativeTo(rbEntryL)}")
    println("  C1 (Fork Exit): ${fork.apply(0.0,0.0,180.0).relativeTo(rbEntryL)}")
    println("  C2 (Straight Entry): ${Pose(0.0, 0.0, 180.0).relativeTo(rbEntryL)}")
}
