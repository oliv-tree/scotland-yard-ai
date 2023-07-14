package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class ShallowMind implements Ai {

	@Nonnull @Override public String name() { return "ShallowMind"; }

	@Nonnull @Override public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
		MutableValueGraph stateGraph = generateGraph(board);
		Move bestMove = pickBestMove(stateGraph, board);
		return bestMove;
	}

	public Move pickBestMove(MutableValueGraph stateGraph, Board board) {
		GraphNode topNode = new GraphNode((Board.GameState) board, null); // get root of the tree
		Set<GraphNode> nextNodes = stateGraph.successors(topNode); // get all nodes in layer below root (i.e. all MrX moves)
		Move bestMove = null;
		int bestScore = 0;
		for (GraphNode node : nextNodes) { // find the node which has the highest score
			int nodeScore = (Integer) stateGraph.edgeValue(topNode, node).get(); // get the score associated with this MrX move
			int detectiveScore = 0;
			Set<GraphNode> nextNodesDetective = stateGraph.successors(node); // get all the nodes in layer below this MrX move (i.e. all possible detective moves afterwards)
			if (!nextNodesDetective.isEmpty()) { // make sure not empty (i.e. move before the last move), else dividing by 0
				for (GraphNode nodeDetective : nextNodesDetective) {
					detectiveScore += (Integer) stateGraph.edgeValue(node, nodeDetective).get(); // get the score associated with this detective move
				}
				detectiveScore = detectiveScore / nextNodesDetective.size(); // get the average of the scores for the possible detective moves from this MrX move
			}
			if (nodeScore + detectiveScore > bestScore) { // update best move if necessary
				bestMove = node.moveMade();
				bestScore = nodeScore + detectiveScore;
			}
		}
		return bestMove;
	}

	public int scoreState(Board.GameState gameState, Move moveMade) {
		int NEARBY_DETECTIVE_PENALTY = 10;
		int FREEDOM_NODES_MULTIPLIER = 4;
		int UNNECESSARY_DOUBLEMOVE_PENALTY = 20;
		int REPEATED_MOVE_PENALTY = 1;

		int mrXSourceLocation = moveMade.source(); // MrX location before move made
		boolean isDetectiveNearby = false;

		int newMrXLocation;
		int score = 0;

		if (moveMade instanceof Move.SingleMove) { // get MrX destination if single move made
			newMrXLocation = ((Move.SingleMove) moveMade).destination;
		}
		else { // get MrX destination if double move made
			newMrXLocation = ((Move.DoubleMove) moveMade).destination2;
		}

		Set <Integer> adjacentNodesBefore = gameState.getSetup().graph.adjacentNodes(mrXSourceLocation); // MrX's adjacentNodes before move
		Set <Integer> adjacentNodesAfter = gameState.getSetup().graph.adjacentNodes(newMrXLocation); // MrX's adjacentNodes after move

		ImmutableSet<Piece> players = gameState.getPlayers();
		int totalDistanceFromDetectives = 0;

		for (Piece player : players) {
			if (player.isMrX()) continue; // exclude mrX
			Optional<Integer> detectiveLocation = gameState.getDetectiveLocation((Piece.Detective) player); // get location of detective
			if (detectiveLocation.isEmpty()) { // check to be sure we have a location, this shouldn't happen
				throw new IllegalArgumentException("No detective location found.");
			}
			if (adjacentNodesBefore.contains(detectiveLocation.get())) {
				isDetectiveNearby = true; // this detective is in the nodes adjacent to MrX before the move is made
			}
			if (adjacentNodesAfter.contains(detectiveLocation.get())) {
				score -= NEARBY_DETECTIVE_PENALTY; // punish move because it will result in MrX being directly next to a detective
			}
			totalDistanceFromDetectives += Math.abs(newMrXLocation - detectiveLocation.get()); // get absolute value of naive distance from mrX (after move made) to this detective
		}

		int averageDistanceFromDetectives = totalDistanceFromDetectives / (players.size() - 1); // divide by number of detectives to get average
		averageDistanceFromDetectives += adjacentNodesAfter.size() * FREEDOM_NODES_MULTIPLIER; // give higher score for moves that result in more freedom
		score = averageDistanceFromDetectives;
		if (moveMade instanceof Move.DoubleMove && !isDetectiveNearby) {
			score -= UNNECESSARY_DOUBLEMOVE_PENALTY; // we want to save double move when there is no detective nearby
		}
		if (newMrXLocation == mrXSourceLocation) {
			score -= REPEATED_MOVE_PENALTY; // punish move where we end up in the same place
		}
		return score;
	}

	public MutableValueGraph generateGraph(Board board) {
		Board.GameState gameState = (Board.GameState) board;
		MutableValueGraph<GraphNode, Integer> stateGraph = ValueGraphBuilder.directed().build();
		ImmutableList<Move> movesMrX = gameState.getAvailableMoves().asList(); // all possible MrX moves
		GraphNode topNode = new GraphNode(gameState, null);; // initial gamestate
		for (Move MrXMove : movesMrX) {
			int score = scoreState(gameState, MrXMove); // score the move we want to make
			Board.GameState gameStateTempMrX = gameState.advance(MrXMove); // establish the new gamestate after move has been made
			GraphNode tempNodeMrX = new GraphNode(gameStateTempMrX, MrXMove); // add new gamestate and movemade to node
			stateGraph.putEdgeValue(topNode, tempNodeMrX, score); // add node and score to the graph
			ImmutableSet<Move> resultantMovesDetective = gameStateTempMrX.getAvailableMoves(); // all possible moves for detectives after this MrX move
			for (Move detectiveMove : resultantMovesDetective) {
				Board.GameState gameStateTempDetective = gameStateTempMrX.advance(detectiveMove); // make the detective move
				GraphNode tempNodeDetective = new GraphNode(gameStateTempDetective, detectiveMove); // make node with this gamestate and the required detective move
				stateGraph.putEdgeValue(tempNodeMrX, tempNodeDetective, scoreState(gameStateTempDetective, detectiveMove)); // add node with its score in layer beneath original MrX move
			}
		}
		return stateGraph;
	}
}
