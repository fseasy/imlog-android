package top.fseasy.imlog.domain

class DbUnexpectedResultException(tableSource: String, message: String) :
    RuntimeException("Db Table $tableSource SQL result get unexpected: $message")

class PathResolveException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)


class FileIoException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
