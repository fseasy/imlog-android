package top.fseasy.imlog.domain

class DbUnexpectedResultException(tableSource: String, message: String) :
    RuntimeException("Db Table $tableSource SQL result get unexpected: $message")
