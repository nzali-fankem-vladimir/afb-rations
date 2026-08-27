package cm.afrilandfirstbank.rations.commun.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Construit le contenu de {@code detail_json}, en JSON valide : le delta
 * avant/apres d'une modification ({@link #champ}), ou le contexte d'une action
 * sans delta comme un refus d'acces ({@link #contexte}).
 *
 * <p>Le Sprint 1.2 formait ce JSON a la main, par concatenation de chaines. Le
 * cas y etait sans danger (un enum et un code a cinq chiffres), mais le motif ne
 * doit pas se propager a six services : une valeur portant un guillemet, un
 * antislash ou un saut de ligne produirait un JSON invalide, et la trace
 * deviendrait illisible cote service Audit — silencieusement, puisque personne
 * ne relit un journal d'audit avant d'en avoir besoin. Jackson echappe
 * correctement, ce qui retire le sujet.
 *
 * <p>Usage :
 * <pre>{@code
 * String delta = DeltaAudit.nouveau()
 *         .champ("role", roleAvant, roleApres)
 *         .champ("codeUnite", codeUniteAvant, codeUniteApres)
 *         .enJson();
 * }</pre>
 *
 * <p>Produit :
 * {@code {"role":{"avant":"AGENT_UNITE","apres":"CHEF_UNITE_DA"},
 * "codeUnite":{"avant":"00002","apres":"00003"}}}
 *
 * <p><b>Tous les champs fournis sont inscrits, modifies ou non.</b> Un delta qui
 * n'enumererait que les valeurs changees obligerait le lecteur a deviner si un
 * champ absent est inchange ou n'a pas ete considere. Pour une piece de controle
 * interne, l'exhaustivite prime sur la concision.
 */
public final class DeltaAudit {

    /** {@code ObjectMapper} est immuable une fois configure, donc partageable. */
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    /** LinkedHashMap : l'ordre de declaration des champs est conserve dans la trace. */
    private final Map<String, Object> champs = new LinkedHashMap<>();

    private DeltaAudit() {
    }

    public static DeltaAudit nouveau() {
        return new DeltaAudit();
    }

    /**
     * Ajoute un champ au delta. Les valeurs nulles sont conservees telles quelles
     * (JSON {@code null}) : « le code unite est passe de 00002 a rien » est une
     * information, pas une absence d'information.
     */
    public DeltaAudit champ(String nom, Object avant, Object apres) {
        Map<String, Object> valeurs = new LinkedHashMap<>();
        valeurs.put("avant", avant);
        valeurs.put("apres", apres);
        champs.put(nom, valeurs);
        return this;
    }

    /**
     * Ajoute une valeur de contexte, sans avant ni apres.
     *
     * <p>Toutes les actions tracees ne sont pas des modifications. Un refus
     * d'acces (CT-04) n'a pas de delta : ce qu'il faut consigner, c'est qui a
     * tente quoi, et pourquoi cela a ete refuse. Le meme echappement Jackson
     * s'applique, ce qui compte d'autant plus ici : le contexte d'un refus
     * contient des valeurs venues de l'exterieur, jamais du code.
     */
    public DeltaAudit contexte(String nom, Object valeur) {
        champs.put(nom, valeur);
        return this;
    }

    /**
     * @return le delta en JSON, ou {@code null} si aucun champ n'a ete ajoute —
     *         {@code detail_json} est nullable, et un objet vide n'apporterait rien
     */
    public String enJson() {
        if (champs.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(champs);
        } catch (JsonProcessingException echec) {
            // Un delta inserialisable ne doit pas empecher la trace d'exister :
            // mieux vaut un evenement au detail manquant qu'aucun evenement.
            return null;
        }
    }

}
