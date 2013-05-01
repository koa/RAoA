<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core"%>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Edit Settings</title>
<style type="text/css">
body,input {
	font-family: sans-serif;
	line-height: 1.25em;
	font-size: 100%;
}

form {
	width: 50em;
	padding: 10px;
	background-color: #cccc99;
	padding: 10px;
}

label {
	width: 13em;
	float: left;
}

input {
	border: none;
	background-color: #dede99;
	margin: 5px;
	padding: 2px 2px 2px 5px;
}

.submit,.reset {
	background-color: #cccc99;
}

.error {
	color: red;
}
</style>
</head>
<body>
	<form:form method="POST" commandName="settingsData">
		<form:label path="instanceName">Name of local instance</form:label>
		<form:input path="instanceName" cssErrorClass="error" />
		<form:errors cssClass="error" path="instanceName" />
		<br>
		<form:label path="archiveName">Name of Archive</form:label>
		<form:input path="archiveName" cssErrorClass="error" />
		<form:errors cssClass="error" path="archiveName" />
		<br>
		<form:label path="albumPath">Path to local Album</form:label>
		<form:input path="albumPath" cssErrorClass="error" />
		<form:errors cssClass="error" path="albumPath" />
		<br>
		<form:label path="importBasePath">Path for importing</form:label>
		<form:input path="importBasePath" cssErrorClass="error" />
		<form:errors cssClass="error" path="importBasePath" />
		<br>
		<input type="submit" value="Update" />
	</form:form>
</body>
</html>