package org.openRealmOfStars.gui.scheme;

import java.awt.Color;

/**
*
* Open Realm of Stars game project
* Copyright (C) 2023 Tuomo Untinen
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
* Base scheme which all scheme extend.
*
*/
public abstract class BaseScheme {

  /**
   * Get Panel background color. This is used almost every panel.
   * @return Color
   */
  public abstract Color getPanelBackground();

  /**
   * Get cool space color.
   * @return Color
   */
  public abstract Color getCoolSpaceColor();

  /**
   * Get cool space color dark.
   * @return Color
   */
  public abstract Color getCoolSpaceColorDark();

  /**
   * Get cool space color transparent.
   * @return Color
   */
  public abstract Color getCoolSpaceColorTransparent();

  /**
   * Get deep space color.
   * @return Color
   */
  public abstract Color getDeepSpaceColor();

  /**
   * Get deep space darker color.
   * @return Color
   */
  public abstract Color getDeepSpaceDarkerColor();

  /**
   * Get Info text color.
   * @return Color
   */
  public abstract Color getInfoTextColor();
  /**
   * Get Scheme type
   * @return Scheme Type
   */
  public abstract SchemeType getType();
}
