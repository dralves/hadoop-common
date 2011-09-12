/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.datanode.web.resources;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CreateFlag;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.hdfs.DFSClient;
import org.apache.hadoop.hdfs.DFSOutputStream;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.namenode.NameNode;
import org.apache.hadoop.hdfs.web.WebHdfsFileSystem;
import org.apache.hadoop.hdfs.web.resources.BlockSizeParam;
import org.apache.hadoop.hdfs.web.resources.BufferSizeParam;
import org.apache.hadoop.hdfs.web.resources.OverwriteParam;
import org.apache.hadoop.hdfs.web.resources.Param;
import org.apache.hadoop.hdfs.web.resources.PermissionParam;
import org.apache.hadoop.hdfs.web.resources.PostOpParam;
import org.apache.hadoop.hdfs.web.resources.PutOpParam;
import org.apache.hadoop.hdfs.web.resources.ReplicationParam;
import org.apache.hadoop.hdfs.web.resources.UriFsPathParam;
import org.apache.hadoop.io.IOUtils;

/** Web-hdfs DataNode implementation. */
@Path("")
public class DatanodeWebHdfsMethods {
  private static final Log LOG = LogFactory.getLog(DatanodeWebHdfsMethods.class);

  private @Context ServletContext context;

  /** Handle HTTP PUT request. */
  @PUT
  @Path("{" + UriFsPathParam.NAME + ":.*}")
  @Consumes({"*/*"})
  @Produces({MediaType.APPLICATION_JSON})
  public Response put(
      final InputStream in,
      @PathParam(UriFsPathParam.NAME) final UriFsPathParam path,
      @QueryParam(PutOpParam.NAME) @DefaultValue(PutOpParam.DEFAULT)
          final PutOpParam op,
      @QueryParam(PermissionParam.NAME) @DefaultValue(PermissionParam.DEFAULT)
          final PermissionParam permission,
      @QueryParam(OverwriteParam.NAME) @DefaultValue(OverwriteParam.DEFAULT)
          final OverwriteParam overwrite,
      @QueryParam(BufferSizeParam.NAME) @DefaultValue(BufferSizeParam.DEFAULT)
          final BufferSizeParam bufferSize,
      @QueryParam(ReplicationParam.NAME) @DefaultValue(ReplicationParam.DEFAULT)
          final ReplicationParam replication,
      @QueryParam(BlockSizeParam.NAME) @DefaultValue(BlockSizeParam.DEFAULT)
          final BlockSizeParam blockSize
      ) throws IOException, URISyntaxException {

    if (LOG.isTraceEnabled()) {
      LOG.trace(op + ": " + path
            + Param.toSortedString(", ", permission, overwrite, bufferSize,
                replication, blockSize));
    }

    final String fullpath = path.getAbsolutePath();
    final DataNode datanode = (DataNode)context.getAttribute("datanode");

    switch(op.getValue()) {
    case CREATE:
    {
      final Configuration conf = new Configuration(datanode.getConf());
      final InetSocketAddress nnRpcAddr = NameNode.getAddress(conf);
      final DFSClient dfsclient = new DFSClient(nnRpcAddr, conf);
      final FSDataOutputStream out = new FSDataOutputStream(dfsclient.create(
          fullpath, permission.getFsPermission(), 
          overwrite.getValue() ? EnumSet.of(CreateFlag.CREATE, CreateFlag.OVERWRITE)
              : EnumSet.of(CreateFlag.CREATE),
          replication.getValue(), blockSize.getValue(), null,
          bufferSize.getValue()), null);
      try {
        IOUtils.copyBytes(in, out, bufferSize.getValue());
      } finally {
        out.close();
      }
      final InetSocketAddress nnHttpAddr = NameNode.getHttpAddress(conf);
      final URI uri = new URI(WebHdfsFileSystem.SCHEME, null,
          nnHttpAddr.getHostName(), nnHttpAddr.getPort(), fullpath, null, null);
      return Response.created(uri).type(MediaType.APPLICATION_JSON).build();
    }
    default:
      throw new UnsupportedOperationException(op + " is not supported");
    }
  }

  /** Handle HTTP POST request. */
  @POST
  @Path("{" + UriFsPathParam.NAME + ":.*}")
  @Consumes({"*/*"})
  @Produces({MediaType.APPLICATION_JSON})
  public Response post(
      final InputStream in,
      @PathParam(UriFsPathParam.NAME) final UriFsPathParam path,
      @QueryParam(PostOpParam.NAME) @DefaultValue(PostOpParam.DEFAULT)
          final PostOpParam op,
      @QueryParam(BufferSizeParam.NAME) @DefaultValue(BufferSizeParam.DEFAULT)
          final BufferSizeParam bufferSize
      ) throws IOException, URISyntaxException {

    if (LOG.isTraceEnabled()) {
      LOG.trace(op + ": " + path
            + Param.toSortedString(", ", bufferSize));
    }

    final String fullpath = path.getAbsolutePath();
    final DataNode datanode = (DataNode)context.getAttribute("datanode");

    switch(op.getValue()) {
    case APPEND:
    {
      final Configuration conf = new Configuration(datanode.getConf());
      final InetSocketAddress nnRpcAddr = NameNode.getAddress(conf);
      final DFSClient dfsclient = new DFSClient(nnRpcAddr, conf);
      final DFSOutputStream dfsout = dfsclient.append(fullpath,
          bufferSize.getValue(), null);
      final FSDataOutputStream out = new FSDataOutputStream(dfsout, null,
          dfsout.getInitialLen());
      try {
        IOUtils.copyBytes(in, out, bufferSize.getValue());
      } finally {
        out.close();
      }
      return Response.ok().type(MediaType.APPLICATION_JSON).build();
    }
    default:
      throw new UnsupportedOperationException(op + " is not supported");
    }
  }
}