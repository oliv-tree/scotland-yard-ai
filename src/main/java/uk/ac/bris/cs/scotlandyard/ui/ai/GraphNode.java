package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

import java.util.Objects;

public final class GraphNode {
    private final Board.GameState gameState;
    private final Move moveMade;

    GraphNode(Board.GameState gameState, Move moveMade) {
        this.gameState = gameState;
        this.moveMade = moveMade;
    }

    public Board.GameState gameState() {
        return gameState;
    }

    public Move moveMade() {
        return moveMade;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof GraphNode) {
            GraphNode that = (GraphNode) other;
            if (moveMade != null) { // check both if moveMade isn't null (top node)
                return this.gameState.equals(that.gameState) && this.moveMade.equals(that.moveMade);
            }
            else { // if null then only compare gameState
                return this.gameState.equals(that.gameState);
            }

        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameState, moveMade);
    }

    @Override
    public String toString() {
        return gameState.toString() + moveMade;
    }
}