package org.openRealmOfStars.utilities.json.values;

import java.util.ArrayList;

import org.openRealmOfStars.utilities.json.JsonStream;

/**
*
* Open Realm of Stars game project
* Copyright (C) 2020 Tuomo Untinen
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
* Object Value for Json
*
*/
public class ObjectValue implements JsonValue {

  /**
   * Array of members
   */
  private ArrayList<Member> arrayMember;

  /**
   * Constructor for Object value
   * @param first First member for object
   */
  public ObjectValue(final Member first) {
    arrayMember = new ArrayList<>();
    arrayMember.add(first);
  }

  /**
   * Get array of members.
   * @return Array list of members.
   */
  public ArrayList<Member> getMembers() {
    return arrayMember;
  }
  @Override
  public String getValueAsString() {
    StringBuilder sb = new StringBuilder();
    sb.append(JsonStream.CH_BEGIN_OBJECT);
    for (int i = 0; i < arrayMember.size(); i++) {
      Member value = arrayMember.get(i);
      sb.append(value.getValueAsString());
      if (i < arrayMember.size() - 1) {
        sb.append(JsonStream.CH_VALUE_SEPARATOR);
      }
    }
    sb.append(JsonStream.CH_END_OBJECT);
    return sb.toString();
  }

  @Override
  public ValueType getType() {
    return ValueType.OBJECT;
  }

}
