package com.mybank.autostone.remote;
import lombok.Getter;
@Getter
public class RemoteCommandResult {
 private final boolean success; private final String pid; private final String rawOutput; private final String errorMessage; private final String logPath;
 public RemoteCommandResult(boolean success,String pid,String rawOutput,String errorMessage){this(success,pid,rawOutput,errorMessage,null);}
 public RemoteCommandResult(boolean success,String pid,String rawOutput,String errorMessage,String logPath){this.success=success;this.pid=pid;this.rawOutput=rawOutput;this.errorMessage=errorMessage;this.logPath=logPath;}
}
