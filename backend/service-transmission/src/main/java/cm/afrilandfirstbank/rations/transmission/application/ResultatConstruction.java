package cm.afrilandfirstbank.rations.transmission.application;

import java.util.List;

import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent;

/**
 * Les deux issues de la construction d'une charge : elle part, ou elle ne part pas.
 *
 * <p><b>Aucune troisieme issue, et surtout aucune charge « partiellement valide ».</b>
 * Une fois l'evenement publie sur {@code rations.etat.valide}, le module n'a aucun
 * moyen de le rattraper : la comptabilite fabriquera ses ecritures a partir de ce
 * qu'elle a recu. Publier une charge amputee de ses lignes fautives produirait des
 * beneficiaires impayes que rien ne signalerait — un silence pire qu'un refus bruyant.
 *
 * <p>Type scelle : le {@code switch} qui les traite est exhaustif, et une troisieme
 * issue ajoutee plus tard ferait echouer la compilation plutot que de se glisser
 * silencieusement dans un {@code else}.
 */
public sealed interface ResultatConstruction {

    /** La charge est complete et coherente : elle peut partir telle quelle. */
    record ChargeConstruite(EtatValideEvent charge) implements ResultatConstruction {
    }

    /**
     * La charge ne part pas. {@code anomalies} est non vide et porte <b>toutes</b> les
     * raisons relevees, pas seulement la premiere : corriger l'origine du probleme
     * demande de les voir ensemble.
     */
    record ChargeRefusee(List<AnomalieCharge> anomalies) implements ResultatConstruction {

        public ChargeRefusee {
            if (anomalies == null || anomalies.isEmpty()) {
                throw new IllegalArgumentException(
                        "Un refus de publication sans aucune anomalie serait un refus sans "
                                + "motif : la charge doit pouvoir etre corrigee a la lecture du "
                                + "journal.");
            }
            anomalies = List.copyOf(anomalies);
        }

    }

}
