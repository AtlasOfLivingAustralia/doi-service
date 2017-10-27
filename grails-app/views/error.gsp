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
		<div class="col-xs-12 col-sm-12 col-md-12 col-lg-12">
			<div class="alert alert-danger">
                ${error ?: 'An unexpected error has occurred'}
            </div>
        </div>
    </div>
</div>

</body>
</html>
