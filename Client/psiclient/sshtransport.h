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

#pragma once

#include "transport.h"
#include "transport_registry.h"

class SessionInfo;


//
// Base class for the SSH transports
//
class SSHTransportBase: public ITransport
{
public:
    SSHTransportBase(); 
    virtual ~SSHTransportBase();

    // Subclasses must implement this member
    virtual tstring GetTransportName() const = 0;

    virtual tstring GetSessionID(SessionInfo sessionInfo) const;
    virtual int GetLocalProxyParentPort() const;
    virtual tstring GetLastTransportError() const;

    virtual bool Cleanup();

protected:
    // ITransport implementation
    virtual void TransportConnect(const SessionInfo& sessionInfo);
    virtual bool DoPeriodicCheck();

    // Subclasses must implement this member
    virtual bool GetSSHParams(
                    const SessionInfo& sessionInfo,
                    tstring& o_serverAddress, 
                    tstring& o_serverPort, 
                    tstring& o_serverHostKey, 
                    tstring& o_plonkCommandLine) = 0;

    void TransportConnectHelper(const SessionInfo& sessionInfo);
    bool IsServerSSHCapable(const SessionInfo& sessionInfo) const;
    bool LaunchPlonk(const TCHAR* plonkCommandLine);

protected:
    tstring m_plonkPath;
    PROCESS_INFORMATION m_plonkProcessInfo;
};


//
// Standard SSH
//
class SSHTransport: public SSHTransportBase
{
public:
    SSHTransport(); 
    virtual ~SSHTransport();

    static void GetFactory(tstring& o_transportName, TransportFactory& o_transportFactory);

    virtual tstring GetTransportName() const;

protected:
    virtual bool GetSSHParams(
                    const SessionInfo& sessionInfo,
                    tstring& o_serverAddress, 
                    tstring& o_serverPort, 
                    tstring& o_serverHostKey, 
                    tstring& o_plonkCommandLine);
};


//
// Obfuscated SSH
//
class OSSHTransport: public SSHTransportBase
{
public:
    OSSHTransport(); 
    virtual ~OSSHTransport();

    static void GetFactory(tstring& o_transportName, TransportFactory& o_transportFactory);

    virtual tstring GetTransportName() const;

protected:
    virtual bool GetSSHParams(
                    const SessionInfo& sessionInfo,
                    tstring& o_serverAddress, 
                    tstring& o_serverPort, 
                    tstring& o_serverHostKey, 
                    tstring& o_plonkCommandLine);
};
