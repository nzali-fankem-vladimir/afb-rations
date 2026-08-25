package cm.afrilandfirstbank.rations.identite.api;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cm.afrilandfirstbank.rations.identite.domaine.exception.UtilisateurNonHabiliteException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduit les exceptions du domaine en reponses au format d'erreur uniforme.
 *
 * <p>Les 401 ne passent pas ici : un jeton absent ou invalide est refuse par le
 * filtre de securite, en amont des controleurs.
 */
@RestControllerAdvice
public class GestionnaireErreursApi {

    @ExceptionHandler(UtilisateurNonHabiliteException.class)
    public ResponseEntity<ErreurApiDto> nonHabilite(UtilisateurNonHabiliteException exception,
            HttpServletRequest requete) {
        // Authentification reussie mais habilitation absente : 403, pas 401
        // (document maitre, section 7.1).
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "UTILISATEUR_NON_HABILITE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

}
