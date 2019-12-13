package fr.insee.rmes.api.classifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.insee.rmes.api.MetadataApi;
import fr.insee.rmes.modeles.classification.Poste;
import fr.insee.rmes.modeles.classification.PosteJson;
import fr.insee.rmes.modeles.classification.PosteXml;
import fr.insee.rmes.modeles.classification.Postes;
import fr.insee.rmes.queries.classifications.ClassificationsQueries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Path("/nomenclature")
@Tag(name = "nomenclature", description = "Nomenclature API")
public class ClassificationApi extends MetadataApi {

    private static Logger logger = LogManager.getLogger(ClassificationApi.class);

    @GET
    @Path("/{code}/postes")
    @Produces({
        MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML
    })
    @Operation(
        operationId = "getClassificationByCode",
        summary = "Liste des postes d'une nomenclature (autres que \"catégories juridiques\")",
        responses = {
            @ApiResponse(content = @Content(schema = @Schema(implementation = Postes.class)))
        })
    public Response getClassificationByCode(
        @Parameter(
            required = true,
            description = "Identifiant de la nomenclature (hors cj)") @PathParam("code") String code,
        @Parameter(hidden = true) @HeaderParam(value = HttpHeaders.ACCEPT) String header) {
        logger.debug("Received GET request for classification {}", code);


        final String csvResult = sparqlUtils.executeSparqlQuery(ClassificationsQueries.getClassification(code));
        final List<Poste> itemsList = csvUtils.populateMultiPOJO(csvResult, Poste.class);

        if (itemsList.isEmpty()) {
            return Response.status(Status.NOT_FOUND).entity("").build();
        }
        else if (header.equals(MediaType.APPLICATION_XML)) {
            final List<? extends Poste> itemsListXml = csvUtils.populateMultiPOJO(csvResult, PosteXml.class);
            return Response.ok(responseUtils.produceResponse(new Postes(itemsListXml), header)).build();
        }
        else {
            final List<? extends Poste> itemsListJson =
                csvUtils.populateMultiPOJO(csvResult, PosteJson.class);
            return Response.ok(responseUtils.produceResponse(itemsListJson, header)).build();
        }
    }

    @GET
    @Path("/{code}/arborescence")
    @Produces({
        MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML
    })
    @Operation(
        operationId = "getClassificationTreeByCode",
        summary = "Liste des postes d'une nomenclature (autres que \"catégories juridiques\")",
        responses = {
            @ApiResponse(content = @Content(schema = @Schema(implementation = Postes.class)))
        })
    public Response getClassificationTreeByCode(
        @Parameter(
            required = true,
            description = "Identifiant de la nomenclature (hors cj)") @PathParam("code") String code,
        @Parameter(hidden = true) @HeaderParam(value = HttpHeaders.ACCEPT) String header) {
        logger.debug("Received GET request for classification tree {}", code);

        final String csvResult = sparqlUtils.executeSparqlQuery(ClassificationsQueries.getClassification(code));
        final List<? extends Poste> itemsList = csvUtils.populateMultiPOJO(csvResult, Poste.class);

        if (itemsList.isEmpty()) {
            return Response.status(Status.NOT_FOUND).entity("").build();
        }

        if (header.equals(MediaType.APPLICATION_XML)) {
            final List<PosteXml> root = this.getTree(csvResult, PosteXml.class);
            return Response.ok(responseUtils.produceResponse(new Postes(root), header)).build();
        }
        else {
            final List<PosteJson> root = this.getTree(csvResult, PosteJson.class);
            return Response.ok(responseUtils.produceResponse(new Postes(root), header)).build();
        }
    }

    private <P> List<P> getTree(String csvResult, Class<P> posteClass) {
        final List<P> root = new ArrayList<>();
        final List<P> liste = csvUtils.populateMultiPOJO(csvResult, posteClass);
        final Map<String, P> postes =
            liste.stream().collect(Collectors.toMap(p -> ((Poste) p).getCode(), Function.identity()));

        for (final P poste : liste) {
            if (StringUtils.isNotEmpty(((Poste) poste).getCodeParent())) {
                final P posteParent = postes.get(((Poste) poste).getCodeParent());
                ((Poste) posteParent).addPosteEnfant((Poste) poste);
            }
            else {
                root.add(poste);
            }
        }
        return root;
    }

}
