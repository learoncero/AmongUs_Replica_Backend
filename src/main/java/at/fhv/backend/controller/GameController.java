package at.fhv.backend.controller;

import at.fhv.backend.model.ApiResponse;
import at.fhv.backend.model.Game;
import at.fhv.backend.model.Player;
import at.fhv.backend.model.Position;
import at.fhv.backend.model.messages.CreateGameMessage;
import at.fhv.backend.model.messages.PlayerJoinMessage;
import at.fhv.backend.model.messages.PlayerKillMessage;
import at.fhv.backend.model.messages.PlayerMoveMessage;
import at.fhv.backend.service.GameService;
import at.fhv.backend.service.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api")
public class GameController {
    private final GameService gameService;
    private final PlayerService playerService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public GameController(GameService gameService, PlayerService playerservice, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.playerService = playerservice;
        this.messagingTemplate = messagingTemplate;
    }


    @PostMapping("/game")
    public ResponseEntity<Game> createGame(@RequestBody CreateGameMessage createGameMessage) throws Exception {
        System.out.println("Received request to create game with username: " + createGameMessage.getPlayer().getUsername() + " number of players: " + createGameMessage.getNumberOfPlayers() + " number of impostors: " + createGameMessage.getNumberOfImpostors() + " map: " + createGameMessage.getMap());

        Game createdGame = gameService.createGame(createGameMessage.getPlayer(), createGameMessage.getNumberOfPlayers(), createGameMessage.getNumberOfImpostors(), createGameMessage.getMap());

        if (createdGame != null) {
            return ResponseEntity.ok(createdGame);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/game/{gameCode}")
    public ResponseEntity<Game> getGameByCode(@PathVariable String gameCode) {
//        System.out.println("Received request to get game with code: " + gameCode);
        Game game = gameService.getGameByCode(gameCode);
        if (game != null) {
            return ResponseEntity.ok(game);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/game/join/{playerName}")
    public ResponseEntity<?> joinGame(@RequestBody PlayerJoinMessage joinMessage, @PathVariable String playerName) {
        if (joinMessage == null || joinMessage.getPosition() == null || joinMessage.getGameCode() == null) {
            System.out.println("Invalid join message");
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Invalid join message", null));
        }

        try {
            Game game = gameService.getGameByCode(joinMessage.getGameCode());

            if (game == null) {
                System.out.println("Game not found");
                return ResponseEntity.notFound().build();
            }

            if (game.getPlayers().size() >= game.getNumberOfPlayers()) {
                System.out.println("Game lobby is full");
                return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Game lobby is full", null));
            }

            if (game.getPlayers().stream().anyMatch(p -> p.getUsername().equals(joinMessage.getUsername()))) {
                System.out.println("Username is already taken");
                return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Username is already taken", null));
            }

            Player player = playerService.createPlayer(joinMessage.getUsername(), game);
            game.getPlayers().add(player);

            //Assign roles randomly to players
            game.setPlayers(playerService.setRandomRole(game.getPlayers()));
            return ResponseEntity.ok().body(game);
        } catch (Exception e) {
            System.out.println("Error creating player: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(500, "Error creating player: " + e.getMessage(), null));
        }
    }


    //Todo: check if null
    @MessageMapping("/{gameCode}/play")
    @SendTo("/topic/{gameCode}/play")
    public Game playGame(@RequestBody Game gameToPlay) {
        Game game = gameService.startGame(gameToPlay.getGameCode());
        gameService.setGameAttributes(gameToPlay.getGameCode(), gameToPlay.getPlayers());
        /*System.out.println("Received request to play game with: " + gameToPlay.getGameCode() +
                ", Player1: " + gameToPlay.getPlayers().get(0).getUsername() +
                ", Position: " + gameToPlay.getPlayers().get(0).getPosition().getX());*/
        /*System.out.println("Game that got returned: " + game.getGameCode() +
                ", Player1: " + game.getPlayers().get(0).getUsername() +
                ", Position: " + game.getPlayers().get(0).getPosition().getX());*/

        /*System.out.println("Player id and their roles in GameController: ");
        for (int i = 0; i < game.getPlayers().size(); i++) {
            System.out.println("Player id: " + game.getPlayers().get(i).getId() +
                    " Role: " + game.getPlayers().get(i).getRole());
        }*/

        return game;
    }

    @MessageMapping("/move")
    @SendTo("/topic/positionChange")
    public ResponseEntity<Game> movePlayer(@Payload PlayerMoveMessage playerMoveMessage) {
        int playerId = playerMoveMessage.getId();
        Game game = gameService.getGameByCode(playerMoveMessage.getGameCode());
        Player player = game.getPlayers().stream().filter(p -> p.getId() == playerId).findFirst().orElse(null);

        if (player != null) {
            Position newPosition = playerService.calculateNewPosition(player.getPosition(), playerMoveMessage.getKeyCode());
            playerService.updatePlayerPosition(player, newPosition);
            return ResponseEntity.ok().body(game);
        }

        return ResponseEntity.notFound().build();
    }

    @MessageMapping("/game/kill")
    @SendTo("/topic/playerKill")
    public ResponseEntity<Game> handleKill(@Payload PlayerKillMessage playerKillMessage) {
        int playerToKillId = Integer.parseInt(playerKillMessage.getPlayerToKillId());
        String gameCode = playerKillMessage.getGameCode();
        System.out.println("Kill Request received. GameCode: " + gameCode + " PlayerId to be killed: " + playerToKillId);
        Game game = gameService.killPlayer(gameCode, playerToKillId);
        if (game != null) {
            return ResponseEntity.ok().body(game);
        }
        return ResponseEntity.notFound().build();
    }
}