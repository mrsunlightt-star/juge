package com.juge.app.data

/**
 * 《用户协议》与《隐私政策》全文。
 *
 * 与代码实际行为保持一致，修改数据实践时必须同步更新本文件：
 * - 金句内容、分类、样式、背景图 → 仅存本机（DbHelper / SharedPreferences）
 * - 相册图片 → 仅在用户主动通过系统选择器挑选后读取
 * - 账号（可选）→ 注册/登录时向服务端传输用户名与口令（HTTPS 传输，服务端仅存 PBKDF2 派生结果），用于换机找回 PRO
 * - 支付 → 由支付宝完成，本应用不接触银行卡与支付凭证；服务端仅留档订单号、金额、状态、支付宝交易号与买家标识用于对账
 */
object LegalDocs {

    const val APP_NAME = "句阁"
    const val SITE = "https://puretxt.cn"
    const val ENTITY = "于都县虞都之南科技工作室"
    const val EMAIL = "787415091@qq.com"
    /** App 备案号（工信部 App 备案，与网站备案 赣ICP备2026021624号-1 同主体不同编号） */
    const val ICP_LICENSE = "赣ICP备2026021624号-2A"
    const val ICP_QUERY_URL = "https://beian.miit.gov.cn/"

    val USER_AGREEMENT: String = """
一、协议的接受

欢迎使用《句阁》（英文名 DeskQuotes，以下简称"本应用"）。本应用是一款运行于安卓系统的桌面组件（Widget）工具，用于在手机桌面展示您自定义的文字内容并支持样式个性化设置。

在使用本应用前，请您完整阅读本协议。当您点击"同意并继续"，即表示您已阅读、理解并同意本协议全部内容；如您不同意，请点击"不同意并退出"停止使用。

二、服务内容

1. 桌面组件：在系统桌面上添加 4×2、4×4 等尺寸的组件，展示您录入的文字内容。
2. 内容管理：录入、修改、删除文字内容，并可按自建分类整理。
3. 样式自定义：内置多套组件风格，支持背景颜色、背景图片、透明度、圆角、字体、字号、加粗、斜体、颜色、阴影、对齐等参数的调整。
4. 双路径调整：既可在应用内完整编辑，也可点击桌面组件弹出快捷调整面板。
5. 会员服务：付费激活后永久解锁全部内置风格及后续版本新增风格。

三、账号（可选）

本应用不登录也可完整使用全部功能。您可以选择注册账号，唯一收益是在更换设备或重装应用后找回已购买的会员权益。

账号口令通过 HTTPS 加密传输；服务端仅保存其不可逆派生结果（PBKDF2-HMAC-SHA256），不保存明文口令。由于本应用未提供短信或邮件通道，账号口令无法自助找回；如遗忘，需凭支付宝交易记录联系我们人工处理。

如您不再使用账号，可在应用内「账号」面板中自助注销。注销后服务端的账号与订单绑定信息将被删除且不可恢复。

四、付费与激活

1. 会员价格为一次性买断，永久有效，后续无订阅费用。
2. 支付通过支付宝完成。具体金额以应用内激活面板展示为准。
3. 未登录时购买，会员权益仅绑定当前设备；登录后购买，会员权益同时绑定账号，可在其他设备登录后找回。
4. 由于安卓系统的限制，更换设备或重装应用后，本地记录可能丢失，请通过登录账号找回已购权益。
5. 如需退款，请依据支付宝平台的相关规则处理。

五、用户行为规范

您录入的文字内容、导入的背景图片均保存在您自己的设备本地。您应对所录入内容的合法性负责，不得录入侵犯他人合法权益的内容。本应用不提供内容发布或分享功能，不会将您的内容公开展示。

六、知识产权

本应用及其内置的风格、图标、字体、插画等资源的知识产权归开发者或相应权利人所有。您不得对本应用进行反向工程、破解、二次打包或用于商业转售。

七、免责与责任限制

1. 桌面组件的显示效果依赖手机系统桌面的实现，不同厂商系统（如 HarmonyOS、MIUI、EMUI、ColorOS、OriginOS）可能存在组件数量上限、自动清理、刷新策略差异等情况，本应用无法保证在所有机型上表现完全一致。
2. 因您自行卸载应用、清除应用数据、更换设备导致的本地内容与激活记录丢失，本应用不承担赔偿责任。
3. 在法律允许的最大范围内，本应用对间接损失不承担责任。

八、协议变更

我们可能随版本更新调整本协议。协议更新后，我们会在新版本中重新向您展示。若您继续使用，视为接受更新后的协议。

九、其他

本协议适用中华人民共和国法律。本应用的运营主体为$ENTITY，官方网站为$SITE，联系邮箱：$EMAIL。
    """.trimIndent()

    val PRIVACY_POLICY: String = """
本政策说明《句阁》如何收集、使用、存储和保护您的信息。我们遵循"最小必要"原则：本应用的核心功能完全在您的设备本地完成。

一、我们收集哪些信息

本应用不收集您的通讯录、位置、通话记录、短信、设备标识等信息，也未接入任何广告 SDK 或统计分析 SDK。
唯一会处理个人信息的第三方是支付环节的支付宝 SDK——它只在您主动点击支付时启动，具体见第六节第 3 项。

二、仅保存在您设备本地的信息

以下信息全部存储在您的手机本地，我们不会上传：

1. 您录入的文字内容与自建分类；
2. 组件的样式配置（背景颜色、背景图片、透明度、圆角、字体、字号、阴影、对齐等）；
3. 您导入的背景图片文件；
4. 会员激活状态与激活记录。

三、相册权限

当您主动为组件选择背景图片时，本应用会调用系统图片选择器读取您挑选的那张图片。图片仅在您确认选择后才会导入本应用，未经您选择，本应用不会读取相册中的任何内容。本应用不申请后台读取相册的权限。

四、账号信息（仅当您主动注册时）

不注册账号时，本应用不向任何服务器发送数据。当您主动注册或登录账号时，我们会向服务端传输：

1. 您设置的用户名；
2. 您设置的口令。口令通过 HTTPS 加密传输，服务端收到后立即以 PBKDF2-HMAC-SHA256 加盐派生，仅保存派生结果，不保存明文口令；
3. 登录成功后签发的访问令牌，服务端仅保存其摘要（SHA-256），明文令牌只返回给您一次。

上述信息仅用于账号登录与会员权益找回，不用于任何其他用途。

五、支付信息

支付由支付宝完成。本应用不接触、不存储您的银行卡号、支付密码或支付宝账号。支付完成后，服务端会记录以下与订单相关的信息，仅用于对账与权益找回：

1. 本应用生成的订单号、商品名称、订单金额、订单与支付状态、支付完成时间；
2. 支付宝在支付结果中返回的交易号，以及买家标识（支付宝用户号、买家登录名），用于订单核对与纠纷处理；
3. 在您已登录时，记录该订单与账号的绑定关系，用于换机后找回权益。

上述信息不会用于广告或用户画像。支付宝 SDK 在支付过程中可能按其自身规则处理必要信息，相关处理适用《支付宝隐私权政策》。

六、信息的共享与转让

我们不会向任何第三方出售、出租或交易您的个人信息。除以下情形外，不对外提供：

1. 您主动选择使用支付宝支付时，为完成支付所必需的信息传递；
2. 法律法规要求或司法、行政机关依法要求提供；
3. 第三方 SDK 清单：本应用只接入一个会处理个人信息的第三方 SDK，且只在您主动点击「支付」时才会启动。

   名称：支付宝支付 SDK（由支付宝（中国）网络技术有限公司提供）
   使用目的：拉起支付宝收银台完成支付、查询支付结果、安全风控。
   可能处理的个人信息：设备信息（设备标识符如 IMEI/IMSI/AndroidID、MAC 地址、SIM 卡信息、BSSID/SSID、Wi-Fi 列表、设备序列号）、应用列表与运行中应用信息、位置信息、剪切板信息、传感器信息。以上为该 SDK 官方说明中列明的采集范围，实际采集取决于支付流程与您的系统设置。
   隐私政策：https://render.alipay.com/p/c/k2cx0tg8

   除上述 SDK 外，本应用未接入广告、统计、推送、社交、地图等任何其他第三方 SDK。

七、数据安全

我们采取以下措施保护您的信息：口令使用 PBKDF2-HMAC-SHA256 派生后存储；访问令牌仅保存摘要；账号登录失败次数超限时进行短时锁定，防止口令被暴力尝试。本地数据依托安卓系统提供的应用私有存储目录隔离。

关于安卓系统备份：若您开启了系统的「云备份」或「换机迁移」功能，安卓可能自动备份本应用的部分数据。我们已排除其中的敏感状态——账号登录令牌、隐私同意状态、会员激活记录与组件绑定关系均不参与备份；您的文字内容、分类、样式配置、自定义背景图片与自定义颜色会参与备份，以便换机后恢复。您可以随时在系统设置中关闭本应用的备份。

八、您的权利

1. 查看与修改：您可在应用内随时查看、修改、删除您的文字内容、分类与样式配置。
2. 删除：卸载本应用或清除应用数据，即可删除全部本地数据。
3. 账号注销：您可在应用内「账号」面板中自助注销账号。注销后，服务端保存的账号与订单绑定信息将被删除且无法恢复，您将无法再通过该账号找回会员权益（本机已解锁的功能不受影响）。您也可通过下方联系方式提出注销请求，我们将在核实后处理。
4. 撤回同意：您可以随时撤回对本政策的同意——在系统设置中清除本应用数据或直接卸载本应用即可，撤回后本应用将不再处理您的信息（此前基于您的同意已完成的处理不受影响）。撤回同意后，需要重新同意才能继续使用本应用。

九、未成年人保护

本应用不面向 14 周岁以下儿童单独提供服务。若您是未成年人，请在监护人陪同下阅读本政策并使用本应用。

十、政策更新

本政策可能随功能调整而更新。更新后我们会在新版本中重新向您展示。若涉及信息处理方式的重大变化，我们会以显著方式提示。

十一、联系我们

本应用运营主体：$ENTITY
官方网站：$SITE
联系邮箱：$EMAIL

如您对本政策有任何疑问、意见或投诉，或需要注销账号、删除服务端保存的账号与订单信息，请通过上述邮箱与我们联系，我们将在核实后处理。
    """.trimIndent()

    /**
     * 《开源许可》——内置字体授权情况。
     *
     * 内置字体均为第三方开源字体，为减小安装包体积已做子集化裁剪（按《通用规范汉字表》
     * 8105 字 + 常用符号，属于对字体的修改，仅删除冷僻字形，不改动保留字形的轮廓）。
     * 各字体的版权与授权声明如下，与 tools/fonts/README.md 保持一致。
     */
    val OPEN_SOURCE_LICENSES: String = """
本应用内置的字体均为第三方开源字体。为减小安装包体积，这些字体已按《通用规范汉字表》（8105 字）及常用符号做了子集化裁剪（属于对字体的修改），裁剪仅删除冷僻字形，不改动保留字形的轮廓。

各字体的版权与授权声明如下，授权条款全文附于其后。

一、字体清单与授权

（一）SIL Open Font License 1.1 授权

1. 思源黑体 Source Han Sans CN
版权：© 2014-2025 Adobe (http://www.adobe.com/)，保留字体名 'Source'
授权：SIL Open Font License 1.1 — https://scripts.sil.org/OFL

2. 思源宋体 Source Han Serif CN
版权：© 2017-2024 Adobe (http://www.adobe.com/)，保留字体名 'Source'
授权：SIL Open Font License 1.1 — https://scripts.sil.org/OFL

3. 霞鹜文楷 LXGW WenKai GB
版权：Copyright 2022-2026 LXGW (https://github.com/lxgw/LxgwWenkaiGB)；Copyright 2020 The Klee Project Authors
授权：SIL Open Font License 1.1 — https://scripts.sil.org/OFL

4. 毛笔楷书 Ma Shan Zheng
版权：Copyright 2018 The MaShanZheng Project Authors (https://github.com/googlefonts/mashanzheng)
授权：SIL Open Font License 1.1 — https://scripts.sil.org/OFL

5. jf open 粉圆 JF Open Huninn
版权：Copyright 2020-2024 justfont Co., LTD. (https://github.com/justfont/open-huninn-font)，保留字体名 'open huninn'、'huninn'；汉字部分改作自 Kosugi Maru (Copyright 2010 MOTOYA CO.,LTD.)，拉丁部分改作自 Varela Round (Copyright 2011-2016 The Varela Round Project Authors)
授权：SIL Open Font License 1.1 — https://scripts.sil.org/OFL

（二）IPA Font License 1.0 授权

6. 霞鹜新致宋 LXGW Neo ZhiSong
版权：Copyright(c) 2023-2026 LXGW；Information-technology Promotion Agency, Japan (IPA), 2003-2019
授权：IPA Font License 1.0 — https://opensource.org/licenses/IPA/

7. 霞鹜新晰黑 Screen LXGW Neo XiHei Screen
版权：Copyright(c) 2021-2026 LXGW；Information-technology Promotion Agency, Japan (IPA), 2003-2019
授权：IPA Font License 1.0 — https://opensource.org/licenses/IPA/

二、SIL Open Font License 1.1 全文

SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide development of collaborative font projects, to support the font creation efforts of academic and linguistic communities, and to provide a free and open framework in which fonts may be shared and improved in partnership with others.

The OFL allows the licensed fonts to be used, studied, modified and redistributed freely as long as they are not sold by themselves. The fonts, including any derivative works, can be bundled, embedded, redistributed and/or sold with any software provided that any reserved names are not used by derivative works. The fonts and derivatives, however, cannot be released under any other type of license. The requirement for fonts to remain under this license does not apply to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright Holder(s) under this license and clearly marked as such. This may include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the copyright statement(s).

"Original Version" refers to the collection of Font Software components as distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting, or substituting -- in part or in whole -- any of the components of the Original Version, by changing formats or by porting the Font Software to a new environment.

"Author" refers to any designer, engineer, programmer, technical writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining a copy of the Font Software, to use, study, copy, merge, embed, modify, redistribute, and sell modified and unmodified copies of the Font Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components, in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled, redistributed and/or sold with any software, provided that each copy contains the above copyright notice and this license. These can be included either as stand-alone text files, human-readable headers or in the appropriate machine-readable metadata fields within text or binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font Name(s) unless explicit written permission is granted by the corresponding Copyright Holder. This restriction only applies to the primary font name as presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font Software shall not be used to promote, endorse or advertise any Modified Version, except to acknowledge the contribution(s) of the Copyright Holder(s) and the Author(s) or with their explicit written permission.

5) The Font Software, modified or unmodified, in part or in whole, must be distributed entirely under this license, and must not be distributed under any other license. The requirement for fonts to remain under this license does not apply to any document created using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM OTHER DEALINGS IN THE FONT SOFTWARE.

三、IPA Font License Agreement v1.0 全文

IPA Font License Agreement v1.0

The Licensor provides the Licensed Program (as defined in Article 1 below) under the terms of this license agreement ("Agreement"). Any use, reproduction or distribution of the Licensed Program, or any exercise of rights under this Agreement by a Recipient (as defined in Article 1 below) constitutes the Recipient's acceptance of this Agreement.

Article 1 (Definitions)

1. "Digital Font Program" shall mean a computer program containing, or used to render or display fonts.

2. "Licensed Program" shall mean a Digital Font Program licensed by the Licensor under this Agreement.

3. "Derived Program" shall mean a Digital Font Program created as a result of a modification, addition, deletion, replacement or any other adaptation to or of a part or all of the Licensed Program, and includes a case where a Digital Font Program newly created by retrieving font information from a part or all of the Licensed Program or Embedded Fonts, from a Digital Document File with or without modification of the retrieved font information.

4. "Digital Content" shall mean products provided to end users in the form of digital data, including video content, motion and/or still pictures, TV programs or other broadcasting content and products consisting of character text, pictures, photographic images, graphic symbols and/or the like.

5. "Digital Document File" shall mean a PDF file or other Digital Content created by various software programs in which a part or all of the Licensed Program becomes embedded or contained in the file for the display of the font ("Embedded Fonts"). Embedded Fonts are used only in the display of characters in the particular Digital Document File within which they are embedded, and shall be distinguished from those in any Digital Font Program, which may be used for display of characters outside that particular Digital Document File.

6. "Computer" shall include a server in this Agreement.

7. "Reproduction and Other Exploitation" shall mean reproduction, transfer, distribution, lease, public transmission, presentation, exhibition, adaptation and any other exploitation.

8. "Recipient" shall mean anyone who receives the Licensed Program under this Agreement, including one that receives the Licensed Program from a Recipient.

Article 2 (Grant of License)

The Licensor grants to the Recipient a license to use the Licensed Program in any and all countries in accordance with each of the provisions set forth in this Agreement. However, any and all rights underlying in the Licensed Program shall be held by the Licensor. In no sense is this Agreement intended to transfer any right relating to the Licensed Program held by the Licensor except as specifically set forth herein or any right relating to any trademark, trade name, or service mark to the Recipient.

1. The Recipient may install the Licensed Program on any number of Computers and use the same in accordance with the provisions set forth in this Agreement.

2. The Recipient may use the Licensed Program, with or without modification in printed materials or in Digital Content as an expression of character texts or the like.

3. The Recipient may conduct Reproduction and Other Exploitation of the printed materials and Digital Content created in accordance with the preceding Paragraph, for commercial or non-commercial purposes and in any form of media including but not limited to broadcasting, communication and various recording media.

4. If any Recipient extracts Embedded Fonts from a Digital Document File to create a Derived Program, such Derived Program shall be subject to the terms of this agreement.

5. If any Recipient performs Reproduction or Other Exploitation of a Digital Document File in which Embedded Fonts of the Licensed Program are used only for rendering the Digital Content within such Digital Document File then such Recipient shall have no further obligations under this Agreement in relation to such actions.

6. The Recipient may reproduce the Licensed Program as is without modification and transfer such copies, publicly transmit or otherwise redistribute the Licensed Program to a third party for commercial or non-commercial purposes ("Redistribute"), in accordance with the provisions set forth in Article 3 Paragraph 2.

7. The Recipient may create, use, reproduce and/or Redistribute a Derived Program under the terms stated above for the Licensed Program: provided, that the Recipient shall follow the provisions set forth in Article 3 Paragraph 1 when Redistributing the Derived Program.

Article 3 (Restriction)

The license granted in the preceding Article shall be subject to the following restrictions:

1. If a Derived Program is Redistributed pursuant to Paragraph 4 and 6 of the preceding Article, the following conditions must be met:

(1) The following must be also Redistributed together with the Derived Program, or be made available online or by means of mailing mechanisms in exchange for a cost which does not exceed the total costs of postage, storage medium and handling fees:

(a) a copy of the Derived Program; and

(b) any additional file created by the font developing program in the course of creating the Derived Program that can be used for further modification of the Derived Program, if any.

(2) It is required to also Redistribute means to enable recipients of the Derived Program to replace the Derived Program with the Licensed Program first released under this License (the "Original Program"). Such means may be to provide a difference file from the Original Program, or instructions setting out a method to replace the Derived Program with the Original Program.

(3) The Recipient must license the Derived Program under the terms and conditions of this Agreement.

(4) No one may use or include the name of the Licensed Program as a program name, font name or file name of the Derived Program.

(5) Any material to be made available online or by means of mailing a medium to satisfy the requirements of this paragraph may be provided, verbatim, by any party wishing to do so.

2. If the Recipient Redistributes the Licensed Program pursuant to Paragraph 5 of the preceding Article, the Recipient shall meet all of the following conditions:

(1) The Recipient may not change the name of the Licensed Program.

(2) The Recipient may not alter or otherwise modify the Licensed Program.

(3) The Recipient must attach a copy of this Agreement to the Licensed Program.

3. THIS LICENSED PROGRAM IS PROVIDED BY THE LICENSOR "AS IS" AND ANY EXPRESSED OR IMPLIED WARRANTY AS TO THE LICENSED PROGRAM OR ANY DERIVED PROGRAM, INCLUDING, BUT NOT LIMITED TO, WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY, OR FITNESS FOR A PARTICULAR PURPOSE, ARE DISCLAIMED. IN NO EVENT SHALL THE LICENSOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXTENDED, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO; PROCUREMENT OF SUBSTITUTED GOODS OR SERVICE; DAMAGES ARISING FROM SYSTEM FAILURE; LOSS OR CORRUPTION OF EXISTING DATA OR PROGRAM; LOST PROFITS), HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE INSTALLATION, USE, THE REPRODUCTION OR OTHER EXPLOITATION OF THE LICENSED PROGRAM OR ANY DERIVED PROGRAM OR THE EXERCISE OF ANY RIGHTS GRANTED HEREUNDER, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.

4. The Licensor is under no obligation to respond to any technical questions or inquiries, or provide any other user support in connection with the installation, use or the Reproduction and Other Exploitation of the Licensed Program or Derived Programs thereof.

Article 4 (Termination of Agreement)

1. The term of this Agreement shall begin from the time of receipt of the Licensed Program by the Recipient and shall continue as long as the Recipient retains any such Licensed Program in any way.

2. Notwithstanding the provision set forth in the preceding Paragraph, in the event of the breach of any of the provisions set forth in this Agreement by the Recipient, this Agreement shall automatically terminate without any notice. In the case of such termination, the Recipient may not use or conduct Reproduction and Other Exploitation of the Licensed Program or a Derived Program: provided that such termination shall not affect any rights of any other Recipient receiving the Licensed Program or the Derived Program from such Recipient who breached this Agreement.

Article 5 (Governing Law)

1. IPA may publish revised and/or new versions of this License. In such an event, the Recipient may select either this Agreement or any subsequent version of the Agreement in using, conducting the Reproduction and Other Exploitation of, or Redistributing the Licensed Program or a Derived Program. Other matters not specified above shall be subject to the Copyright Law of Japan and other related laws and regulations of Japan.

2. This Agreement shall be construed under the laws of Japan.

四、说明

以上字体仅用于在桌面组件中渲染您录入的文字。若您是相关字体的权利人并认为本应用的使用方式不妥，请通过下方邮箱联系我们，我们将及时处理。

联系邮箱：$EMAIL
    """.trimIndent()

    /** 协议/政策/许可全文的展示标题 */
    fun titleOf(key: String): String = when (key) {
        "user" -> "《用户协议》"
        "privacy" -> "《隐私政策》"
        "license" -> "《开源许可》"
        else -> ""
    }

    /** 协议/政策/许可全文正文 */
    fun bodyOf(key: String): String = when (key) {
        "user" -> USER_AGREEMENT
        "privacy" -> PRIVACY_POLICY
        "license" -> OPEN_SOURCE_LICENSES
        else -> ""
    }
}