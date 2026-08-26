package cm.afrilandfirstbank.rations.identite.domaine;

import java.util.Objects;
import java.util.Set;

/**
 * Portee d'acces d'un utilisateur aux dossiers d'une unite, document de
 * conception section 10.
 *
 * <p>Deux formes : nationale (toutes les unites, pas de liste a enumerer) ou
 * limitee a un ensemble fini de codes unite. Une instance ne melange jamais
 * les deux : {@link #codesUnite()} n'a de sens que pour une portee non
 * nationale.
 */
public final class PorteeAcces {

    private final boolean nationale;
    private final Set<String> codesUnite;

    private PorteeAcces(boolean nationale, Set<String> codesUnite) {
        this.nationale = nationale;
        this.codesUnite = codesUnite;
    }

    public static PorteeAcces nationale() {
        return new PorteeAcces(true, Set.of());
    }

    public static PorteeAcces limiteeA(String codeUnite) {
        Objects.requireNonNull(codeUnite, "codeUnite requis pour une portee limitee");
        return new PorteeAcces(false, Set.of(codeUnite));
    }

    public boolean estNationale() {
        return nationale;
    }

    /** Codes unite accessibles. Vide pour une portee nationale : toutes les unites, sans enumeration. */
    public Set<String> codesUnite() {
        return codesUnite;
    }

    public boolean couvre(String codeUnite) {
        return nationale || codesUnite.contains(codeUnite);
    }

}
