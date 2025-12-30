#import "SandboxPathModule.h"

@implementation SandboxPathModule

- (id)getDatabasesDirectoryPath:(NSDictionary *)args {
    // 1. 获取 Documents 目录 URL
    NSArray *documentURLs = [[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory
                                                                     inDomains:NSUserDomainMask];
    NSURL *documentsDir = documentURLs.firstObject;
    if (!documentsDir) {
        return @"获取Documents目录失败";
    }
    
    // 2. 拼接 Databases 子目录 URL
    NSURL *dbDir = [documentsDir URLByAppendingPathComponent:@"Databases"];
    
    // 3. 确保目录存在（不存在则创建，支持多级目录）
    NSError *error = nil;
    BOOL isDirCreated = [[NSFileManager defaultManager] createDirectoryAtURL:dbDir
                                       withIntermediateDirectories:YES
                                                        attributes:nil
                                                             error:&error];
    if (!isDirCreated) {
        // 返回创建失败信息（包含错误描述）
        return [NSString stringWithFormat:@"创建DB目录失败：%@", error.localizedDescription];
    }
    
    // 4. 返回目录的字符串路径（适配 sqlite3_open 接口）
    return dbDir.path;
}
@end
