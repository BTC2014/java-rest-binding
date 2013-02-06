/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.rest.graphdb;

import org.junit.Ignore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

@Ignore
public class CypherTestRunner {
    public static void main(String[] args) throws IOException {
        String uri = (args.length>0) ? args[0] : "http://localhost:7474/db/data";
        RestAPIFacade restAPIFacade = new RestAPIFacade(uri);
        try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String query=null;
        System.out.print("Query: ");
        while ((query=reader.readLine())!=null) {
            Map<?,?> result = restAPIFacade.query(query, null);
            for (Map.Entry<?, ?> row : result.entrySet()) {
                System.out.println(row);
            }
            System.out.print("Query: ");
        }
        } finally {
            restAPIFacade.close();
        }
    }
}
