package org.warps.monitor.crawl.protocol.io;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.VersionMismatchException;
import org.apache.hadoop.io.Writable;
import org.warps.monitor.crawl.protocol.Content;
import org.warps.pulsar.persist.io.MetadataWritable;
import org.warps.pulsar.persist.metadata.Metadata;

import java.io.*;
import java.util.zip.InflaterInputStream;

/**
 * Created by vincent on 17-3-17.
 * Copyright @ 2013-2017 Warpspeed Information. All rights reserved
 */
public class ContentWritable implements Writable {

  public final static int VERSION = -1;

  private Configuration conf;
  private int version;
  private String url;
  private String base;
  private byte[] rawContent;
  private String contentType;
  private MetadataWritable metadataWritable = new MetadataWritable(new Metadata());

  private Content content;

  public ContentWritable(Configuration conf) {
    this.conf = conf;
    this.content = new Content();
  }

  public ContentWritable(Content content) { this.content = content; }

  public Content get() { return content; }

  @Override
  public final void readFields(DataInput in) throws IOException {
    int sizeOrVersion = in.readInt();
    if (sizeOrVersion < 0) { // version
      version = sizeOrVersion;
      switch (version) {
        case VERSION:
          url = Text.readString(in);
          base = Text.readString(in);

          rawContent = new byte[in.readInt()];
          in.readFully(rawContent);

          contentType = Text.readString(in);
          metadataWritable.readFields(in);
          break;
        default:
          throw new VersionMismatchException((byte) VERSION, (byte) version);
      }
    } else { // size
      byte[] compressed = new byte[sizeOrVersion];
      in.readFully(compressed, 0, compressed.length);
      ByteArrayInputStream deflated = new ByteArrayInputStream(compressed);
      DataInput inflater = new DataInputStream(new InflaterInputStream(deflated));
      readFieldsCompressed(inflater);
    }

    this.content = new Content(url, base, rawContent, contentType, metadataWritable.get(), conf);
  }

  private final void readFieldsCompressed(DataInput in) throws IOException {
    byte oldVersion = in.readByte();
    switch (oldVersion) {
      case 0:
      case 1:
        url = Text.readString(in); // read url
        base = Text.readString(in); // read base

        rawContent = new byte[in.readInt()]; // read rawContent
        in.readFully(rawContent);

        contentType = Text.readString(in); // read contentType
        // reconstruct metadata
        int keySize = in.readInt();
        String key;
        for (int i = 0; i < keySize; i++) {
          key = Text.readString(in);
          int valueSize = in.readInt();
          for (int j = 0; j < valueSize; j++) {
            metadataWritable.get().put(key, Text.readString(in));
          }
        }
        break;
      case 2:
        url = Text.readString(in); // read url
        base = Text.readString(in); // read base

        rawContent = new byte[in.readInt()]; // read rawContent
        in.readFully(rawContent);

        contentType = Text.readString(in); // read contentType
        metadataWritable.readFields(in); // read meta data
        break;
      default:
        throw new VersionMismatchException((byte) 2, oldVersion);
    }
  }

  public static Content read(DataInput in, Configuration conf) throws IOException {
    ContentWritable contentWritable = new ContentWritable(conf);
    contentWritable.readFields(in);
    return contentWritable.get();
  }

  public final void write(DataOutput out) throws IOException {
    out.writeInt(VERSION);

    Text.writeString(out, content.getUrl()); // write url
    Text.writeString(out, content.getBaseUrl()); // write base

    out.writeInt(content.getContent().length); // write rawContent
    out.write(content.getContent());

    Text.writeString(out, content.getContentType()); // write contentType

    new MetadataWritable(content.getMetadata()).write(out); // write metadata
  }
}
