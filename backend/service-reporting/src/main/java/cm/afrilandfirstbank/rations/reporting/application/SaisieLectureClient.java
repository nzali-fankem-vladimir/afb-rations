package cm.afrilandfirstbank.rations.reporting.application;

import cm.afrilandfirstbank.rations.reporting.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.reporting.domaine.SessionEnum;

/**
 * Port de lecture vers le service Saisie (Sprint 6.1).
 *
 * <h2>Une seule methode, et elle ne rend que des identifiants</h2>
 *
 * <p>Le reporting ne demande a la Saisie qu'une chose : <b>quels etats contiennent
 * au moins une ligne repondant a ces criteres ?</b> Ni les lignes, ni les
 * beneficiaires, ni les montants — le grain du suivi est l'etat mensuel, pas la
 * prestation.
 *
 * <p>C'est ce qui rend l'agregation tenable : un seul appel, quel que soit le
 * nombre de resultats. Une strategie qui interrogerait la Saisie une fois par etat
 * candidat ferait exploser le temps de reponse, et le guide 6.1 la proscrit
 * explicitement.
 *
 * <p>Le jeton de l'utilisateur final est relaye tel quel : la Saisie resout
 * elle-meme la portee d'acces a partir de lui.
 */
public interface SaisieLectureClient {

    /**
     * @param beneficiaire numero de compte courant exact, ou fragment de nom ou de
     *        prenom, insensible a la casse
     */
    ResultatIdentifiantsAvecLigne identifiantsAvecLigne(Integer mois, Integer annee,
            NatureEnum nature, SessionEnum session, String beneficiaire,
            String enteteAutorisation);

}
