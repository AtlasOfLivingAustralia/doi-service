<!DOCTYPE html>
<html>
<head>
	<meta name="layout" content="main"/>
	<title>ALA DOI Repository</title>

	<!-- HTML5 shim and Respond.js for IE8 support of HTML5 elements and media queries -->
	<!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
	<!--[if lt IE 9]>
        <script src="https://oss.maxcdn.com/html5shiv/3.7.2/html5shiv.min.js"></script>
        <script src="https://oss.maxcdn.com/respond/1.4.2/respond.min.js"></script>
    <![endif]-->

	<asset:stylesheet src="doi.css"/>
</head>

<body>
<ala:systemMessage/>

	<div class="col-sm-12 col-md-9 col-lg-9">

		<h2 class="heading-medium">Error</h2>

		<div class="row">
			<div class="col-sm-12">
				<div class="alert alert-danger">
					Unauthorised
				</div>
			</div>
		</div>
		<div class="row">
			<div class="col-sm-12">
				<p>The file <code>${doi.filename}</code> for <a href="https://doi.org/${doi.doi}" type="button" class="doi doi-sm"><span>DOI</span><span>${doi.doi}</a> contains sensitive data and you don't have the required roles to grant you access to it.</p>

				<p>Please <a href="${grailsApplication.config.ala.contact.form}"> contact us </a>, quoting <a href="https://doi.org/${doi.doi}" type="button" class="doi doi-sm"><span>DOI</span><span>${doi.doi}</a>,  to request access to the file.</p>

			</div>
		</div>
	</div>
</body>
</html>
