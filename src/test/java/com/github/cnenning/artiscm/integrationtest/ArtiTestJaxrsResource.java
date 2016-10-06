package com.github.cnenning.artiscm.integrationtest;

import java.io.InputStream;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

@ApplicationPath("/")
public class ArtiTestJaxrsResource extends Application {

	@GET
	@Path("/app-name")
	@Produces("*/*")
	@Consumes("*/*")
	public Response listVersions() {
		InputStream inputStream = getClass().getResourceAsStream("/versions.html");
		return Response.ok(inputStream).build();
	}

	@GET
	@Path("/app-name/{version}")
	@Produces("*/*")
	@Consumes("*/*")
	@SuppressWarnings("unused")
	public Response listFiles(@PathParam("version") String version) {
		InputStream inputStream = getClass().getResourceAsStream("/files.html");
		return Response.ok(inputStream).build();
	}

	@GET
	@Path("/app-name/{version}/{filename}")
	@Produces("*/*")
	@Consumes("*/*")
	@SuppressWarnings("unused")
	public Response downloadFile(@PathParam("version") String version, @PathParam("filename") String filename) {
		InputStream inputStream = getClass().getResourceAsStream("/" + filename);
		return Response.ok(inputStream).build();
	}
}
