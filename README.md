# 写在前面

## Fork项目

- Fork loks666大佬get_jobs项目，源码地址：https://github.com/loks666/get_jobs
- 新增架构、代码优化、功能完善、bug修复
- 不定期更新，有使用问题欢迎issue留言

# 使用方法

## 1. 下载项目与环境配置

- 下载项目

```
git clone https://github.com/loks666/get_jobs.git
cd get_jobs
```

- 环境配置
  1.环境配置:JDK21、Maven、Chrome
  2.修改配置文件(一般默认即可,需要修改自己的地区和岗位)

```
https://github.com/loks666/get_jobs/wiki/%E7%8E%AF%E5%A2%83%E9%85%8D%E7%BD%AE
```

## 2.命令行执行方法

```
mvn -q -DskipTests `-Dexec.args="zhilian" exec:java
```

到解压目录：

```java
cd e:\VM\wslubuntu\get_jobs-main
进行单个模块运行：
mvn exec:java `-Dexec.args="liepin"   （Windows注意转译字符`）
mvn -q -DskipTests `-Dexec.args="zhilian" exec:java

全部运行：
- 运行所有平台： mvn exec:java （默认行为）
- 只运行boss和liepin： mvn exec:java -Dexec.args="boss liepin"
- 只运行单个平台： mvn exec:java -Dexec.args="liepin"
- 显式运行所有平台： mvn exec:java -Dexec.args="all"

```

## 更新日志

### 2025-10-24

- 优化代码，新增AI接口检测
- 增加多平台支持岗位数据过滤
- 修复部分平台数据获取异常问题

## TODO List

- [ ] Python3重构，新增多线程、协程支持
- [ ] 优化爬取容错机制，增加调试过程函数
