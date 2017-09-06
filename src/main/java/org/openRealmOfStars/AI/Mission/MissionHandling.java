package org.openRealmOfStars.AI.Mission;

import org.openRealmOfStars.AI.PathFinding.AStarSearch;
import org.openRealmOfStars.AI.PathFinding.PathPoint;
import org.openRealmOfStars.audio.soundeffect.SoundPlayer;
import org.openRealmOfStars.game.Game;
import org.openRealmOfStars.game.GameState;
import org.openRealmOfStars.game.States.PlanetBombingView;
import org.openRealmOfStars.mapTiles.Tile;
import org.openRealmOfStars.mapTiles.TileNames;
import org.openRealmOfStars.player.PlayerInfo;
import org.openRealmOfStars.player.SpaceRace.SpaceRace;
import org.openRealmOfStars.player.diplomacy.Attitude;
import org.openRealmOfStars.player.diplomacy.DiplomaticTrade;
import org.openRealmOfStars.player.diplomacy.negotiation.NegotiationType;
import org.openRealmOfStars.player.diplomacy.speeches.SpeechType;
import org.openRealmOfStars.player.fleet.Fleet;
import org.openRealmOfStars.player.fleet.FleetList;
import org.openRealmOfStars.player.ship.Ship;
import org.openRealmOfStars.player.ship.ShipHullType;
import org.openRealmOfStars.player.ship.ShipStat;
import org.openRealmOfStars.starMap.Coordinate;
import org.openRealmOfStars.starMap.Route;
import org.openRealmOfStars.starMap.StarMap;
import org.openRealmOfStars.starMap.StarMapUtilities;
import org.openRealmOfStars.starMap.Sun;
import org.openRealmOfStars.starMap.newsCorp.NewsFactory;
import org.openRealmOfStars.starMap.planet.Planet;
import org.openRealmOfStars.utilities.DiceGenerator;

/**
 *
 * Open Realm of Stars game project
 * Copyright (C) 2016, 2017  Tuomo Untinen
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see http://www.gnu.org/licenses/
 *
 *
 * Mission handling for AI
 *
 */
public final class MissionHandling {

  /**
   * Just hiding MissionHandling constructor
   */
  private MissionHandling() {
    // Hiding mission handling constructor
  }

  /**
   * Handle exploring mission
   * @param mission Exploring mission, does nothing if type is wrong
   * @param fleet Fleet on mission
   * @param info PlayerInfo
   * @param game Game for getting star map and planet
   */
  public static void handleExploring(final Mission mission, final Fleet fleet,
      final PlayerInfo info, final Game game) {
    if (mission != null && mission.getType() == MissionType.EXPLORE) {
      String ignoreSun = null;
      if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getRoute() == null) {
        // Fleet has encounter obstacle, taking a detour round it
        Sun sun = game.getStarMap().locateSolarSystem(fleet.getX(),
            fleet.getY());
        if (sun != null && sun.getName().equals(mission.getSunName())) {
          // Fleet is in correct solar system, starting explore execution mode
          mission.setPhase(MissionPhase.EXECUTING);
          fleet.setaStarSearch(null);
        } else {
          makeReroute(game, fleet, info, mission);
        }
      }
      if (mission.getPhase() == MissionPhase.EXECUTING) {
        mission.setMissionTime(mission.getMissionTime() + 1);
        boolean missionComplete = false;
        if (mission.getMissionTime() >= info.getRace().getAIExploringAmount()) {
          // Depending on race it decides enough is enough
          fleet.setaStarSearch(null);
          ignoreSun = mission.getSunName();
          missionComplete = true;
        }
        if (fleet.getaStarSearch() == null) {
          Sun sun = null;
          if (missionComplete) {
            sun = game.getStarMap().getNearestSolarSystem(fleet.getX(),
                fleet.getY(), info, fleet, ignoreSun);
            if (sun == null) {
              Planet home = game.getStarMap().getClosestHomePort(info,
                  fleet.getCoordinate());
              if (home == null) {
                info.getMissions().remove(mission);
                return;
              }
              mission.setType(MissionType.MOVE);
              mission.setTarget(home.getCoordinate());
              mission.setPhase(MissionPhase.PLANNING);
              mission.setTargetPlanet(home.getName());
            } else {
              if (!sun.getName().equals(mission.getSunName())) {
                mission.setTarget(sun.getCenterCoordinate());
                fleet.setRoute(new Route(fleet.getX(), fleet.getY(),
                    mission.getX(), mission.getY(), fleet.getFleetFtlSpeed()));
                mission.setSunName(sun.getName());
                mission.setPhase(MissionPhase.TREKKING);
                // Starting the new exploring mission
                mission.setMissionTime(0);
                return;
              }
            }
          } else {
            sun = game.getStarMap().getSunByName(mission.getSunName());
          }
          PathPoint point = info.getUnchartedSector(sun, fleet);
          if (point != null) {
            mission.setTarget(new Coordinate(point.getX(), point.getY()));
            AStarSearch search = new AStarSearch(game.getStarMap(),
                fleet.getX(), fleet.getY(), mission.getX(), mission.getY());
            search.doSearch();
            search.doRoute();
            fleet.setaStarSearch(search);
            makeRegularMoves(game, fleet, info);
          }
        } else {
          makeRegularMoves(game, fleet, info);
        }
      }
    } // End Of Explore
  }

  /**
   * Handle Colonize mission
   * @param mission Colonize mission, does nothing if type is wrong
   * @param fleet Fleet on mission
   * @param info PlayerInfo
   * @param game Game for getting star map and planet
   */
  public static void handleColonize(final Mission mission, final Fleet fleet,
      final PlayerInfo info, final Game game) {
    if (mission != null && mission.getType() == MissionType.COLONIZE) {
      if (mission.getPhase() == MissionPhase.LOADING) {
        // Loading colonist
        Planet planet = game.getStarMap().getPlanetByCoordinate(fleet.getX(),
            fleet.getY());
        if (planet.getPlanetPlayerInfo() == info) {
          Ship[] ships = fleet.getShips();
          int colony = 0;
          for (int i = 0; i < ships.length; i++) {
            if (ships[i].isColonyModule()) {
              colony = i;
              break;
            }
          }
          if (planet.getTotalPopulation() > 2 && planet.takeColonist()
              && ships[colony].getFreeCargoColonists() > 0) {
            // One colonist on board, ready to go trekking
            ships[colony].setColonist(ships[colony].getColonist() + 1);
            mission.setPhase(MissionPhase.TREKKING);
            Route route = new Route(fleet.getX(), fleet.getY(), mission.getX(),
                mission.getY(), fleet.getFleetFtlSpeed());
            fleet.setRoute(route);
          }
          if (planet.getTotalPopulation() > 3 && planet.takeColonist()
              && ships[colony].getFreeCargoColonists() > 0) {
            ships[colony].setColonist(ships[colony].getColonist() + 1);
          }
        }
      }
      if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getX() == mission.getX() && fleet.getY() == mission.getY()) {
        // Target acquired
        mission.setPhase(MissionPhase.EXECUTING);
        Ship ship = fleet.getColonyShip();
        Planet planet = game.getStarMap().getPlanetByCoordinate(fleet.getX(),
            fleet.getY());
        if (ship != null && planet != null
            && planet.getPlanetPlayerInfo() == null) {
          // Make sure that ship is really colony and there is planet to
          // colonize
          planet.setPlanetOwner(game.getStarMap().getAiTurnNumber(), info);
          if (info.getRace() == SpaceRace.MECHIONS) {
            planet.setWorkers(Planet.PRODUCTION_WORKERS, ship.getColonist());
          } else {
            planet.setWorkers(Planet.PRODUCTION_FOOD, ship.getColonist());
          }
          // Remove the ship and AI just colonized planet
          info.getMissions().remove(mission);
          fleet.removeShip(ship);
          if (fleet.getNumberOfShip() == 0) {
            // Remove also empty fleet
            info.getFleets().recalculateList();
          }
          ShipStat stat = game.getStarMap().getCurrentPlayerInfo()
              .getShipStatByName(ship.getName());
          stat.setNumberOfInUse(stat.getNumberOfInUse() - 1);
        }

      } else if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getRoute() == null) {
        Coordinate coord = new Coordinate(mission.getX(), mission.getY());
        if (info.getSectorVisibility(coord) == PlayerInfo.VISIBLE) {
          Planet planet = game.getStarMap().getPlanetByCoordinate(coord.getX(),
              coord.getY());
          if (planet.getPlanetOwnerIndex() != -1) {
            // Planet has been colonized so no longer colonization mission.
            Planet homePort = game.getStarMap().getClosestHomePort(info,
                fleet.getCoordinate());
            mission.setTarget(homePort.getCoordinate());
            mission.setTargetPlanet(homePort.getName());
            mission.setMissionTime(0);
            mission.setPhase(MissionPhase.PLANNING);
            mission.setType(MissionType.MOVE);
          }
        }
        makeReroute(game, fleet, info, mission);
      }
    } // End of colonize

  }

  /**
   * Handle Colonize mission
   * @param mission Colonize mission, does nothing if type is wrong
   * @param fleet Fleet on mission
   * @param info PlayerInfo
   * @param game Game for getting star map and planet
   */
  public static void handleDeployStarbase(final Mission mission,
      final Fleet fleet, final PlayerInfo info, final Game game) {
    if (mission != null && mission.getType() == MissionType.DEPLOY_STARBASE) {
      if (mission.getPhase() == MissionPhase.LOADING) {
        mission.setPhase(MissionPhase.TREKKING);
      }
      if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getX() == mission.getX() && fleet.getY() == mission.getY()) {
        // Target acquired
        mission.setPhase(MissionPhase.EXECUTING);
        Tile tile = game.getStarMap().getTile(fleet.getX(), fleet.getY());
        if (tile.getName().equals(TileNames.DEEP_SPACE_ANCHOR1)
            || tile.getName().equals(TileNames.DEEP_SPACE_ANCHOR2)) {
          for (Ship ship : fleet.getShips()) {
            if (ship.getHull().getHullType() == ShipHullType.STARBASE) {
              fleet.removeShip(ship);
              Fleet newFleet = new Fleet(ship, fleet.getX(), fleet.getY());
              FleetList fleetList = info.getFleets();
              newFleet.setName(fleetList.generateUniqueName("Deep Space"));
              ship.setFlag(Ship.FLAG_STARBASE_DEPLOYED, true);
              fleetList.add(newFleet);
            }
          }
          // Remove the ship and AI just colonized planet
          info.getMissions().remove(mission);
          if (fleet.getNumberOfShip() == 0) {
            // Remove also empty fleet
            info.getFleets().recalculateList();
          }
        }
      } else if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getRoute() == null) {
        Coordinate coord = new Coordinate(mission.getX(), mission.getY());
        if (info.getSectorVisibility(coord) == PlayerInfo.VISIBLE) {
          Fleet anotherFleet = game.getStarMap().getFleetByCoordinate(
              mission.getX(), mission.getY());
          if (anotherFleet != null) {
            // Anchor has been taken, no more deploy starbase mission
            Planet homePort = game.getStarMap().getClosestHomePort(info,
                fleet.getCoordinate());
            mission.setTarget(homePort.getCoordinate());
            mission.setTargetPlanet(homePort.getName());
            mission.setMissionTime(0);
            mission.setPhase(MissionPhase.PLANNING);
            mission.setType(MissionType.MOVE);
          }
        }
        makeReroute(game, fleet, info, mission);
      }
    } // End of Deploy starbase

  }

  /**
   * Handle Move mission
   * @param mission Move mission, does nothing if type is wrong
   * @param fleet Fleet on mission
   * @param info PlayerInfo
   * @param game Game for getting star map and planet
   */
  public static void handleMove(final Mission mission, final Fleet fleet,
      final PlayerInfo info, final Game game) {
    if (mission != null && mission.getType() == MissionType.MOVE) {
      if (mission.getPhase() != MissionPhase.TREKKING) {
        Route route = new Route(fleet.getX(), fleet.getY(), mission.getX(),
            mission.getY(), fleet.getFleetFtlSpeed());
        fleet.setRoute(route);
        mission.setPhase(MissionPhase.TREKKING);
      }
      if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getX() == mission.getX() && fleet.getY() == mission.getY()) {
        // Target acquired, mission complete
        info.getMissions().remove(mission);
      } else if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getRoute() == null) {
        makeReroute(game, fleet, info, mission);
      }
    } // End of colonize
  }

  /**
   * Handle Gather mission
   * @param mission Gather mission, does nothing if type is wrong
   * @param fleet Fleet on mission
   * @param info PlayerInfo
   * @param game Game for getting star map and planet
   */
  public static void handleGather(final Mission mission, final Fleet fleet,
      final PlayerInfo info, final Game game) {
    if (mission != null && mission.getType() == MissionType.GATHER) {
      if (mission.getPhase() != MissionPhase.TREKKING) {
        Route route = new Route(fleet.getX(), fleet.getY(), mission.getX(),
            mission.getY(), fleet.getFleetFtlSpeed());
        fleet.setRoute(route);
        mission.setPhase(MissionPhase.TREKKING);
      }
      if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getX() == mission.getX()
          && fleet.getY() == mission.getY()) {
        // Target acquired, mission complete
        String attackFleetName = "Attaker of " +  mission.getTargetPlanet();
        Fleet attackFleet = info.getFleets().getByName(attackFleetName);
        if (attackFleet == null) {
          if (info.getFleets().isUniqueName(attackFleetName, fleet)) {
            fleet.setName("Attacker of " + mission.getTargetPlanet());
          } else {
            fleet.setName(info.getFleets().generateUniqueName(
                attackFleetName));
          }
        } else {
          fleet.setName(info.getFleets().generateUniqueName(
              attackFleetName));
          mergeFleets(attackFleet, info);
        }
        info.getMissions().remove(mission);
      } else if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getRoute() == null) {
        makeReroute(game, fleet, info, mission);
      }
    } // End of colonize

  }


  /**
   * Handle Colonize mission
   * @param mission Colonize mission, does nothing if type is wrong
   * @param fleet Fleet on mission
   * @param info PlayerInfo
   * @param game Game for getting star map and planet
   */
  public static void handleAttack(final Mission mission, final Fleet fleet,
      final PlayerInfo info, final Game game) {
    if (mission != null && mission.getType() == MissionType.ATTACK) {
      if (mission.getPhase() == MissionPhase.PLANNING
          && mission.getTargetPlanet() != null && info.getMissions()
              .isAttackMissionLast(mission.getTargetPlanet())) {
        int bombers = 0;
        int trooper = 0;
        int military = 0;
        for (Ship ship : fleet.getShips()) {
          if (ship.hasBombs()) {
            bombers++;
          }
          if (ship.isTrooperShip()) {
            trooper++;
          }
          if (ship.getTotalMilitaryPower() > 0) {
            military++;
          }
        }
        if (military >= info.getRace().getAIMinimumAttackShips()
            && (bombers + trooper) > info.getRace()
                .getAIMinimumConquerShips()) {
          mission.setPhase(MissionPhase.EXECUTING);
          Planet planet = game.getStarMap()
              .getPlanetByName(mission.getTargetPlanet());
          if (planet != null) {
            mission.setTarget(planet.getCoordinate());
            fleet.setRoute(new Route(fleet.getX(), fleet.getY(), planet.getX(),
                planet.getY(), fleet.getFleetFtlSpeed()));
          }
        }
      }
      if (mission.getPhase() == MissionPhase.LOADING) {
        // Loading Troops
        Planet planet = game.getStarMap().getPlanetByCoordinate(fleet.getX(),
            fleet.getY());
        if (planet == null) {
          if (fleet.getTotalCargoColonist() > 0) {
            mission.setPhase(MissionPhase.TREKKING);
          } else {
            Planet homePort = game.getStarMap().getClosestHomePort(info,
                fleet.getCoordinate());
            if (homePort != null) {
              mission.setType(MissionType.MOVE);
              mission.setTarget(homePort.getCoordinate());
            }
          }
        } else if (planet.getPlanetPlayerInfo() == info) {
          Ship[] ships = fleet.getShips();
          int trooper = 0;
          for (int i = 0; i < ships.length; i++) {
            if (ships[i].isTrooperModule()) {
              trooper = i;
              break;
            }
          }
          if (planet.getTotalPopulation() > 2 && planet.takeColonist()
              && ships[trooper].getFreeCargoColonists() > 0) {
            // One Troops on board, ready to go trekking
            ships[trooper].setColonist(ships[trooper].getColonist() + 1);
            mission.setPhase(MissionPhase.TREKKING);
            Route route = new Route(fleet.getX(), fleet.getY(), mission.getX(),
                mission.getY(), fleet.getFleetFtlSpeed());
            fleet.setRoute(route);
          }
          while (planet.getTotalPopulation() > 3 && planet.takeColonist()
              && ships[trooper].getFreeCargoColonists() > 0) {
            ships[trooper].setColonist(ships[trooper].getColonist() + 1);
          }
        }
      }
      if (mission.getPhase() == MissionPhase.TREKKING
          && mission.getTargetPlanet() == null && fleet.getX() == mission.getX()
          && fleet.getY() == mission.getY()) {
        // Target acquired, merge fleet to bigger attack group
        mergeFleets(fleet, info);
        info.getMissions().remove(mission);
      } else if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getRoute() == null) {
        makeReroute(game, fleet, info, mission);
      }
      if (mission.getPhase() == MissionPhase.EXECUTING
          && fleet.getX() == mission.getX() && fleet.getY() == mission.getY()) {
        // Target acquired, mission completed!
        info.getMissions().remove(mission);
        Planet planet = game.getStarMap().getPlanetByCoordinate(fleet.getX(),
            fleet.getY());
        if (planet == null) {
          return;
        }
        if (planet.getPlanetPlayerInfo().isHuman()) {
          // Bombing human planet
          int attackerIndex = game.getStarMap().getPlayerList().getIndex(info);
          PlanetBombingView bombView = new PlanetBombingView(planet, fleet,
              info, attackerIndex, game);
          game.changeGameState(GameState.PLANETBOMBINGVIEW, bombView);
        } else {
          // Bombing AI planet
          PlanetBombingView bombingView = new PlanetBombingView(planet, fleet,
              info, game.getStarMap().getPlayerList().getIndex(info), game);
          bombingView.setStarMap(game.getStarMap());
          bombingView.handleAiToAiAttack();
        }
      } else if (mission.getPhase() == MissionPhase.EXECUTING
          && fleet.getRoute() == null) {
        makeReroute(game, fleet, info, mission);
      }
    } // End of Attack
  }

  /**
   * Handle Defend mission
   * @param mission Defend mission, does nothing if type is wrong
   * @param fleet Fleet on mission
   * @param info PlayerInfo
   * @param game Game for getting star map and planet
   */
  public static void handleDefend(final Mission mission, final Fleet fleet,
      final PlayerInfo info, final Game game) {
    if (mission != null && mission.getType() == MissionType.DEFEND) {
      if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getX() == mission.getX() && fleet.getY() == mission.getY()) {
        // Target acquired
        mission.setPhase(MissionPhase.EXECUTING);
        // Set defending route
        fleet.setRoute(new Route(fleet.getX(), fleet.getY(), fleet.getX(),
            fleet.getY(), 0));
      } else if (mission.getPhase() == MissionPhase.EXECUTING) {
        mission.setMissionTime(mission.getMissionTime() + 1);
        if (mission.getMissionTime() >= info.getRace().getAIDefenseUpdate()) {
          // New defender is needed
          mission.setMissionTime(0);
          mission.setPhase(MissionPhase.PLANNING);
        }
      } else if (mission.getPhase() == MissionPhase.TREKKING
          && fleet.getRoute() == null) {
        makeReroute(game, fleet, info, mission);
      }
    }
  }

  /**
   * Find ship for gathering mission
   * @param mission Gathering mission
   * @param info Player whose fleets are searched
   */
  public static void findGatheringShip(final Mission mission,
      final PlayerInfo info) {
    if (!mission.getShipType().isEmpty()) {
      for (int j = 0; j < info.getFleets().getNumberOfFleets(); j++) {
        Fleet fleet = info.getFleets().getByIndex(j);
        if (info.getMissions().getMissionForFleet(fleet.getName()) == null) {
          for (Ship ship : fleet.getShips()) {
            Fleet newFleet = null;
            if (mission.getShipType().equals(Mission.ASSAULT_TYPE)
                && ship.getTotalMilitaryPower() > 0) {
              if (fleet.getNumberOfShip() > 1) {
                fleet.removeShip(ship);
                newFleet = new Fleet(ship, fleet.getX(), fleet.getY());
              } else {
                newFleet = fleet;
              }
            }
            if (mission.getShipType().equals(Mission.BOMBER_TYPE)
                && ship.hasBombs()) {
              if (fleet.getNumberOfShip() > 1) {
                fleet.removeShip(ship);
                newFleet = new Fleet(ship, fleet.getX(), fleet.getY());
              } else {
                newFleet = fleet;
              }
            }
            if (mission.getShipType().equals(Mission.TROOPER_TYPE)
                && ship.isTrooperShip()) {
              if (fleet.getNumberOfShip() > 1) {
                fleet.removeShip(ship);
                newFleet = new Fleet(ship, fleet.getX(), fleet.getY());
              } else {
                newFleet = fleet;
              }
            }
            if (newFleet != null) {
              String fleetName;
              for (int k = 0; k < info.getFleets().getNumberOfFleets(); k++) {
                fleetName = "Gather " + mission.getShipType() + " #" + k;
                if (info.getFleets().isUniqueName(fleetName, newFleet)) {
                  newFleet.setName(fleetName);
                  mission.setFleetName(fleetName);
                  mission.setPhase(MissionPhase.TREKKING);
                  // Found correct ship from fleet
                  return;
                }
              }
            }

          }
        }
      }
    }
  }
  /**
   * Merge fleet with in same space and starting with same fleet names
   * @param fleet Fleet where to merge
   * @param info PlayerInfo for both fleets
   */
  public static void mergeFleets(final Fleet fleet, final PlayerInfo info) {
    // Merging fleets
    String[] part = fleet.getName().split("#");
    if (part[0].contains("Scout")
        || part[0].contains("Explorer")) {
      // Do not merge scout fleets.
      return;
    }
    for (int j = 0; j < info.getFleets().getNumberOfFleets(); j++) {
      // Merge fleets in same space with same starting of fleet name
      Fleet mergeFleet = info.getFleets().getByIndex(j);
      if (mergeFleet != fleet && mergeFleet.getX() == fleet.getX()
          && mergeFleet.getY() == fleet.getY()
          && mergeFleet.getName().startsWith(part[0])) {
        for (int k = 0; k < mergeFleet.getNumberOfShip(); k++) {
          Ship ship = mergeFleet.getShipByIndex(k);
          if (ship != null) {
            fleet.addShip(ship);
          }
        }
        info.getFleets().remove(j);
        break;
      }
    }
  }

  /**
   * Handle diplomacy between two AI players
   * @param game Game class
   * @param info First player as PlayerInfo
   * @param secondIndex Second player as a index
   * @param fleet Fleet which crossed then border
   */
  public static void handleDiplomacyBetweenAis(final Game game,
      final PlayerInfo info, final int secondIndex, final Fleet fleet) {
    // For Ai players make offer
    int index = game.getStarMap().getPlayerList().getIndex(info);
    DiplomaticTrade trade = new DiplomaticTrade(game.getStarMap(),
        index, secondIndex);
    if (fleet == null) {
      trade.generateOffer();
    } else {
      trade.generateRecallFleetOffer(fleet);
    }
    if (trade.isOfferGoodForBoth()
        || trade.getFirstOffer().isTypeInOffer(NegotiationType.WAR)) {
      // Another party accepts it or it is war
      trade.doTrades();
      if (trade.getFirstOffer().isTypeInOffer(NegotiationType.WAR)) {
        StarMapUtilities.addWarDeclatingRepuation(game.getStarMap(), info);
        PlayerInfo defender = game.getStarMap().getPlayerByIndex(secondIndex);
        game.getStarMap().getNewsCorpData().addNews(
            NewsFactory.makeWarNews(info, defender, fleet, game.getStarMap()));
      }
      if (trade.getFirstOffer().isTypeInOffer(NegotiationType.ALLIANCE)) {
        PlayerInfo defender = game.getStarMap().getPlayerByIndex(secondIndex);
        game.getStarMap().getNewsCorpData().addNews(
            NewsFactory.makeAllianceNews(info, defender, fleet));
      }
      if (trade.getFirstOffer().isTypeInOffer(NegotiationType.TRADE_ALLIANCE)) {
        PlayerInfo defender = game.getStarMap().getPlayerByIndex(secondIndex);
        game.getStarMap().getNewsCorpData().addNews(
            NewsFactory.makeTradeAllianceNews(info, defender, fleet));
      }
      if (trade.getFirstOffer().isTypeInOffer(NegotiationType.PEACE)) {
        PlayerInfo defender = game.getStarMap().getPlayerByIndex(secondIndex);
        game.getStarMap().getNewsCorpData().addNews(
            NewsFactory.makePeaceNews(info, defender, fleet));
      }
    } else {
      SpeechType type = trade.getSpeechTypeByOffer();
      Attitude attitude = info.getAiAttitude();
      int liking = info.getDiplomacy().getLiking(secondIndex);
      int warChance = DiplomaticTrade.getWarChanceForDecline(type, attitude,
          liking);
      int value = DiceGenerator.getRandom(99);
      if (value < warChance) {
        trade.generateEqualTrade(NegotiationType.WAR);
        trade.doTrades();
        StarMapUtilities.addWarDeclatingRepuation(game.getStarMap(), info);
        PlayerInfo defender = game.getStarMap().getPlayerByIndex(secondIndex);
        game.getStarMap().getNewsCorpData().addNews(
            NewsFactory.makeWarNews(info, defender, fleet, game.getStarMap()));
      }
    }
    trade.updateMeetingNumbers();
  }

  /**
   * Make fleet to move. This checks if there is a fleet in point where moving
   * and checks if there is a war between these players. If not then AI
   * will start diplomacy.
   * @param game Game
   * @param point Point where to move
   * @param info PlayerInfo who is moving
   * @param fleet Fleet which is moving
   */
  private static void makeFleetMove(final Game game, final PathPoint point,
      final PlayerInfo info, final Fleet fleet) {
    StarMap map = game.getStarMap();
    AStarSearch search = fleet.getaStarSearch();
    Fleet fleetAtTarget = map.getFleetByCoordinate(point.getX(), point
        .getY());
    boolean war = false;
    if (fleetAtTarget != null) {
      PlayerInfo infoAtTarget = map.getPlayerInfoByFleet(fleetAtTarget);
      if (info == infoAtTarget) {
        fleetAtTarget = null;
      }
      war = map.isWarBetween(info, infoAtTarget);
    }
    if (war || fleetAtTarget == null) {
      // Not blocked so fleet is moving
      game.fleetMakeMove(info, fleet, point.getX(), point.getY());
      search.nextMove();
    } else {
      fleet.setMovesLeft(0);
      PlayerInfo infoAtTarget = map.getPlayerInfoByFleet(fleetAtTarget);
      if (infoAtTarget != null) {
        if (infoAtTarget.isHuman()) {
          SoundPlayer.playSound(SoundPlayer.RADIO_CALL);
          game.changeGameState(GameState.DIPLOMACY_VIEW, info);
        } else {
          int index = map.getPlayerList().getIndex(infoAtTarget);
          handleDiplomacyBetweenAis(game, info, index, null);
        }
      }
    }
  }

  /**
   * Make fleet to move. This checks if there is a fleet in point where moving
   * and checks if there is a war between these players. If not then AI
   * will start diplomacy.
   * @param game Game
   * @param nx X-Coordinate where to move
   * @param ny Y-Coordinate where to move
   * @param info Playerinfo who owns the fleet
   * @param fleet Fleet which is moving
   */
  public static void makeFleetMove(final Game game, final int nx, final int ny,
      final PlayerInfo info, final Fleet fleet) {
    StarMap map = game.getStarMap();
    Fleet fleetAtTarget = map.getFleetByCoordinate(nx, ny);
    boolean war = false;
    if (fleetAtTarget != null) {
      PlayerInfo infoAtTarget = map.getPlayerInfoByFleet(fleetAtTarget);
      if (info == infoAtTarget) {
        fleetAtTarget = null;
      }
      war = map.isWarBetween(info, infoAtTarget);
    }
    if (war || fleetAtTarget == null) {
      // Not blocked so fleet is moving
      game.fleetMakeMove(info, fleet, nx, ny);
    } else {
      fleet.setMovesLeft(0);
      PlayerInfo infoAtTarget = map.getPlayerInfoByFleet(fleetAtTarget);
      if (infoAtTarget != null) {
        if (infoAtTarget.isHuman()) {
          SoundPlayer.playSound(SoundPlayer.RADIO_CALL);
          game.changeGameState(GameState.DIPLOMACY_VIEW, info);
        } else {
          int index = map.getPlayerList().getIndex(infoAtTarget);
          handleDiplomacyBetweenAis(game, info, index, null);
        }
      }
    }
  }
  /**
   * Make Regular moves according A Star Search path finding
   * @param game Game used to get access star map and planet lists
   * @param fleet Fleet to move
   * @param info Player who controls the fleet
   */
  private static void makeRegularMoves(final Game game, final Fleet fleet,
      final PlayerInfo info) {
    AStarSearch search = fleet.getaStarSearch();
    for (int mv = 0; mv < fleet.getMovesLeft(); mv++) {
      PathPoint point = search.getMove();
      if (point != null
          && !game.getStarMap().isBlocked(point.getX(), point.getY())) {
        makeFleetMove(game, point, info, fleet);
      }
    }
    fleet.setMovesLeft(0);
    if (search.isLastMove()) {
      fleet.setaStarSearch(null);
    }
  }

  /**
   * Make Reroute before FTL
   * @param game Game used to get access star map and planet lists
   * @param fleet Fleet to move
   * @param info PlayerInfo
   * @param mission Which mission
   */
  private static void makeReroute(final Game game, final Fleet fleet,
      final PlayerInfo info, final Mission mission) {
    // Fleet has encounter obstacle, taking a detour round it
    if (fleet.getaStarSearch() == null) {
      // No A star search made yet, so let's do it
      AStarSearch search = new AStarSearch(game.getStarMap(), fleet.getX(),
          fleet.getY(), mission.getX(), mission.getY(), 7);
      search.doSearch();
      search.doRoute();
      fleet.setaStarSearch(search);
      makeRerouteBeforeFTLMoves(game, fleet, info, mission);
    } else {
      makeRerouteBeforeFTLMoves(game, fleet, info, mission);
    }
  }

  /**
   * Make reroute moves while on mission
   * @param game Game used to get access star map and planet lists
   * @param fleet Fleet to move
   * @param info PlayerInfo
   * @param mission Which mission
   */
  private static void makeRerouteBeforeFTLMoves(final Game game,
      final Fleet fleet, final PlayerInfo info, final Mission mission) {
    AStarSearch search = fleet.getaStarSearch();
    for (int mv = 0; mv < fleet.getMovesLeft(); mv++) {
      PathPoint point = search.getMove();
      if (point != null
          && !game.getStarMap().isBlocked(point.getX(), point.getY())) {
        makeFleetMove(game, point, info, fleet);
      }
    }
    fleet.setMovesLeft(0);
    if (search.isLastMove()) {
      if (search.getTargetDistance() > 0) {
        fleet.setRoute(new Route(fleet.getX(), fleet.getY(), mission.getX(),
            mission.getY(), fleet.getFleetFtlSpeed()));
      }
      fleet.setaStarSearch(null);
    }

  }

}
