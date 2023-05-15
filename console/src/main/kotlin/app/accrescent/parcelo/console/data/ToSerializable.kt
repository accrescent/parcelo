package app.accrescent.parcelo.console.data

/**
 * Utility interface to convert database objects into serializable objects
 *
 * T must be a class annotated with @Serializable
 */
interface ToSerializable<T> {
    /**
     * Returns a serializable representation of the database object
     */
    fun serializable(): T
}
