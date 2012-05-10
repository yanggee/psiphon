/*
 * Copyright (c) 2012, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
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
 *
 */

package com.psiphon3;

import android.content.Context;
import android.os.Build;

public class PsiphonConstants
{
    public static Boolean DEBUG = false; // may be changed by activity 
    
    public final static String TAG = "Psiphon";

    public final static String INFO_LINK_URL = "https://sites.google.com/a/psiphon3.com/psiphon3/";
    
    public final static String SERVER_ENTRY_FILENAME = "psiphon_server_entries.json";

    public final static int CLIENT_SESSION_ID_SIZE_IN_BYTES = 16;
    
    public final static int SOCKS_PORT = 1080;
    
    public final static int HTTP_PROXY_PORT = 8080;

    public final static int SESSION_ESTABLISHMENT_TIMEOUT_MILLISECONDS = 20000;
    
    public final static String RELAY_PROTOCOL = "OSSH";
    
    // The character restrictions are dictated by the server.
    public final static String PLATFORM = ("Android_" + Build.VERSION.RELEASE).replaceAll("[^\\w\\-\\.]", "_");
    
    public final static int HTTPS_REQUEST_TIMEOUT = 20000;
    
    public static String EXTERNAL_STORAGE_ROOT(Context context) {
        return "/sdcard/" + context.getString(R.string.app_name);
    }
}
