package io.github.mightguy.microserivce.controller;

import io.github.mightguy.microserivce.model.Response;
import io.github.mightguy.microserivce.service.SpellcheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Edit Distance")
@RequestMapping("/distance")
/**
 * REST controller exposing the edit distance endpoint.
 *
 * @author MighTguY
 * @since 6.7
 */
public class SpellcheckController {

    @Autowired
    SpellcheckService service;

    /**
     *
     * @param sourceWord
     * @param targetWord
     * @param maxEd
     * @return
     */
    @Operation(
            summary = "API for calculating the Edit Distance",
            description = "For given source and target String calculate the Edit Distance")
@ApiResponses(value = {
    @ApiResponse(responseCode = "400", description = "SpellCheckExceptions",
                content = @Content(schema = @Schema(implementation = Response.class))),
    @ApiResponse(responseCode = "200", description = "",
                    content = @Content(schema = @Schema(implementation = Response.class)))})
    @GetMapping("/spellcheck/")
    /**
     * Returns the edit distance between two words.
     *
     * @param sourceWord the source word
     * @param targetWord the target word
     * @param maxEd the optional maximum distance
     * @return the response containing both words and the distance
     */
    public Response getEditDistance(
            final @Valid @RequestParam(name = "source") String sourceWord,
            final @Valid @RequestParam(name = "target") String targetWord,
            final @RequestParam(name = "maxED", required = false) Double maxEd) {
        if (maxEd != null) {
            return service.getEditDistance(sourceWord, targetWord, maxEd);
        }
        return service.getEditDistance(sourceWord, targetWord);
    }
}